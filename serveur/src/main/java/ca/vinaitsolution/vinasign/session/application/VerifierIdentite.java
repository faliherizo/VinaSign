package ca.vinaitsolution.vinasign.session.application;

import ca.vinaitsolution.vinasign.commun.application.port.ExecutionOrganisation;
import ca.vinaitsolution.vinasign.commun.application.port.LectureEnveloppes;
import ca.vinaitsolution.vinasign.commun.application.port.SignataireCourant;
import ca.vinaitsolution.vinasign.commun.domain.IdentifiantOrganisation;
import ca.vinaitsolution.vinasign.commun.domain.IdentiteSignataire;
import ca.vinaitsolution.vinasign.session.application.port.ClesAcces;
import ca.vinaitsolution.vinasign.session.application.port.FournisseurIdentite;
import ca.vinaitsolution.vinasign.session.application.port.VerificationsIdentite;
import ca.vinaitsolution.vinasign.session.domain.LienInvalide;
import ca.vinaitsolution.vinasign.session.domain.VerificationIdentite;
import ca.vinaitsolution.vinasign.session.domain.VerificationIdentiteRefusee;
import ca.vinaitsolution.vinasign.session.domain.VerificationIntrouvable;
import java.util.UUID;

/**
 * Vérification d'identité du signataire (VS-131). Le signataire démarre la vérification depuis sa session et est envoyé chez
 * le fournisseur ; le rappel authentifié du fournisseur conclut la vérification et, réussie, élève la session au niveau
 * configuré (méthode {@code idv}), exigé avant la signature et consigné dans la preuve.
 */
public class VerifierIdentite {

    static final String AMR = "idv";
    static final String ACTEUR = "plateforme:verification-identite";

    /** Démarrage : où envoyer le signataire, et la vérification à suivre. */
    public record Demarrage(UUID verification, String url) {
    }

    /** Sources : signataire courant, enveloppes, vérifications, niveau des sessions, exécution au nom d'une organisation. */
    public record Sources(SignataireCourant signataire, LectureEnveloppes enveloppes, VerificationsIdentite verifications, ClesAcces sessions,
                          ExecutionOrganisation execution) {
    }

    private final Sources sources;
    private final FournisseurIdentite fournisseur;
    private final ServicesSession services;
    private final String niveau;

    public VerifierIdentite(Sources sources, FournisseurIdentite fournisseur, ServicesSession services, String niveau) {
        this.sources = sources;
        this.fournisseur = fournisseur;
        this.services = services;
        this.niveau = niveau;
    }

    public Demarrage demarrer() {
        IdentiteSignataire s = sources.signataire().courant().orElseThrow(LienInvalide::new);
        String langue = sources.enveloppes().dossier(s.enveloppe()).flatMap(d -> d.signataires().stream().filter(x -> x.id().equals(s.signataire())).findFirst())
                .orElseThrow(LienInvalide::new).langue();
        String reference = services.nouveauJeton();
        String url = fournisseur.demarrer(new FournisseurIdentite.Demande(reference, langue));
        VerificationIdentite v = VerificationIdentite.demarrer(services.identifiants().suivant(), s.session(), s.signataire(), fournisseur.code(),
                reference, services.horloge().instant());
        sources.verifications().ajouter(v);
        return new Demarrage(v.id(), url);
    }

    /** État d'une vérification de la session courante (le signataire attend le rappel du fournisseur). */
    public VerificationIdentite consulter(UUID id) {
        IdentiteSignataire s = sources.signataire().courant().orElseThrow(LienInvalide::new);
        return sources.verifications().parId(id).filter(v -> v.session().equals(s.session())).orElseThrow(VerificationIntrouvable::new);
    }

    /** Rappel du fournisseur, hors de toute organisation : authentifié, puis conclu dans l'organisation de la référence. */
    public void conclure(byte[] corps, String signature) {
        FournisseurIdentite.Conclusion c = fournisseur.conclure(corps, signature);
        UUID organisation = sources.verifications().organisationDe(c.reference())
                .orElseThrow(() -> new VerificationIdentiteRefusee("Référence inconnue."));
        sources.execution().pour(new IdentifiantOrganisation(organisation), ACTEUR, () -> {
            VerificationIdentite avant = sources.verifications().parReference(c.reference()).orElseThrow(VerificationIntrouvable::new);
            VerificationIdentite apres = avant.conclure(c.reussie(), c.methode(), c.assurance(), services.horloge().instant());
            if (apres != avant) {
                sources.verifications().remplacer(apres);
                if (apres.estReussie()) {
                    sources.sessions().authentifier(apres.session(), niveau, AMR);
                }
            }
            return null;
        });
    }
}
