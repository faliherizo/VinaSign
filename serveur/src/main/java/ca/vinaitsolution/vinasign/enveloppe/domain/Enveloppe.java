package ca.vinaitsolution.vinasign.enveloppe.domain;

import ca.vinaitsolution.vinasign.commun.domain.IdentifiantOrganisation;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.UnaryOperator;

/**
 * Enveloppe (VS-020 à VS-025) : ce qu'une application fait signer, à qui, dans quel ordre, avant quelle échéance. Toute
 * transition passe par une méthode de ce type ; aucune route ne pose un statut (VS-024). Immuable : chaque transition
 * rend l'enveloppe suivante.
 *
 * @param application    identifiant de client de l'application cliente qui l'a créée
 * @param referenceExterne identifiant de l'objet chez l'application (unique par application)
 */
public record Enveloppe(IdentifiantEnveloppe id, IdentifiantOrganisation organisation, String application, String referenceExterne,
                        ModeEnveloppe mode, StatutEnveloppe statut, List<Signataire> signataires, Instant expireLe, Instant creeLe,
                        Instant modifieLe) {

    public Enveloppe {
        if (id == null || organisation == null || application == null || mode == null || statut == null || expireLe == null
                || creeLe == null || modifieLe == null) {
            throw new IllegalArgumentException("Enveloppe incomplète.");
        }
        referenceExterne = referenceExterne == null ? "" : referenceExterne.strip();
        if (referenceExterne.isEmpty() || referenceExterne.length() > 200) {
            throw new IllegalArgumentException("La référence externe est requise (200 caractères au plus).");
        }
        signataires = signataires == null ? List.of() : List.copyOf(signataires);
    }

    public static Enveloppe creer(IdentifiantEnveloppe id, IdentifiantOrganisation organisation, String application,
                                  String referenceExterne, ModeEnveloppe mode, Instant expireLe, Instant maintenant) {
        if (!expireLe.isAfter(maintenant)) {
            throw new SaisieInvalide("echeance-passee", "L'échéance doit être dans le futur.");
        }
        return new Enveloppe(id, organisation, application, referenceExterne, mode, StatutEnveloppe.BROUILLON, List.of(), expireLe,
                maintenant, maintenant);
    }

    /** Signataires et ordre : seulement en brouillon ; identifiants distincts. */
    public Enveloppe definirSignataires(List<Signataire> nouveaux, Instant le) {
        exiger(statut == StatutEnveloppe.BROUILLON, "Les signataires ne changent plus après l'envoi.");
        Set<UUID> ids = new HashSet<>();
        if (nouveaux.isEmpty() || nouveaux.size() > 50 || !nouveaux.stream().allMatch(s -> ids.add(s.id()))) {
            throw new SaisieInvalide("signataires-invalides", "De 1 à 50 signataires, chacun une seule fois.");
        }
        return avec(StatutEnveloppe.BROUILLON, nouveaux, le);
    }

    /** Envoi : au moins un signataire ; en mode document, un document original confirmé. */
    public Enveloppe envoyer(boolean documentConfirme, Instant le) {
        exiger(statut == StatutEnveloppe.BROUILLON, "Seule une enveloppe en brouillon s'envoie.");
        exiger(le.isBefore(expireLe), "L'échéance est passée.");
        if (signataires.isEmpty()) {
            throw new SaisieInvalide("signataires-requis", "Ajouter au moins un signataire avant l'envoi.");
        }
        if (mode == ModeEnveloppe.DOCUMENT && !documentConfirme) {
            throw new SaisieInvalide("document-requis", "Déposer et confirmer le document avant l'envoi.");
        }
        return avec(StatutEnveloppe.ENVOYEE, signataires, le);
    }

    public Enveloppe annuler(Instant le) {
        exiger(!statut.estTerminal(), "Une enveloppe " + statut.code() + " ne s'annule plus.");
        return avec(StatutEnveloppe.ANNULEE, signataires, le);
    }

    /** Expiration à échéance : seulement une enveloppe envoyée, et seulement une fois l'échéance atteinte. */
    public Enveloppe expirer(Instant le) {
        exiger(statut == StatutEnveloppe.ENVOYEE, "Seule une enveloppe envoyée expire.");
        exiger(!le.isBefore(expireLe), "L'échéance n'est pas atteinte.");
        return avec(StatutEnveloppe.EXPIREE, signataires, le);
    }

    /** Signataires qui peuvent agir maintenant : ceux en attente du plus petit ordre restant (VS-023). */
    public List<Signataire> peuventAgir() {
        if (statut != StatutEnveloppe.ENVOYEE) {
            return List.of();
        }
        int tour = signataires.stream().filter(s -> s.statut() == StatutSignataire.EN_ATTENTE).mapToInt(Signataire::ordre).min()
                .orElse(Integer.MAX_VALUE);
        return signataires.stream().filter(s -> s.statut() == StatutSignataire.EN_ATTENTE && s.ordre() == tour)
                .sorted(Comparator.comparing(Signataire::nom)).toList();
    }

    public Enveloppe signer(UUID signataire, Instant le) {
        return agir(signataire, le, StatutSignataire.SIGNE, StatutEnveloppe.ENVOYEE);
    }

    /** Un refus termine l'enveloppe (VS-045). */
    public Enveloppe refuser(UUID signataire, Instant le) {
        return agir(signataire, le, StatutSignataire.REFUSE, StatutEnveloppe.REFUSEE);
    }

    public boolean toutesSignees() {
        return !signataires.isEmpty() && signataires.stream().allMatch(s -> s.statut() == StatutSignataire.SIGNE);
    }

    /** Complétée seulement après la finalisation réussie (VS-070), donc toutes signatures recueillies. */
    public Enveloppe completer(Instant le) {
        exiger(statut == StatutEnveloppe.ENVOYEE && toutesSignees(), "Une enveloppe ne se complète qu'envoyée et entièrement signée.");
        return avec(StatutEnveloppe.COMPLETEE, signataires, le);
    }

    private Enveloppe agir(UUID signataire, Instant le, StatutSignataire resultat, StatutEnveloppe statutSuivant) {
        exiger(statut == StatutEnveloppe.ENVOYEE, "L'enveloppe n'attend aucune signature.");
        if (peuventAgir().stream().noneMatch(s -> s.id().equals(signataire))) {
            if (signataires.stream().noneMatch(s -> s.id().equals(signataire))) {
                throw new SaisieInvalide("signataire-inconnu", "Ce signataire n'appartient pas à l'enveloppe.");
            }
            throw new HorsTour();
        }
        UnaryOperator<Signataire> changer = s -> s.id().equals(signataire) ? s.avecStatut(resultat, le) : s;
        return avec(statutSuivant, signataires.stream().map(changer).toList(), le);
    }

    private Enveloppe avec(StatutEnveloppe nouveau, List<Signataire> liste, Instant le) {
        return new Enveloppe(id, organisation, application, referenceExterne, mode, nouveau, liste, expireLe, creeLe, le);
    }

    private static void exiger(boolean condition, String motif) {
        if (!condition) {
            throw new TransitionEnveloppeInterdite(motif);
        }
    }
}
