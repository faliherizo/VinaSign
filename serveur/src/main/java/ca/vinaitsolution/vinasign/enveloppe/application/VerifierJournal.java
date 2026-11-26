package ca.vinaitsolution.vinasign.enveloppe.application;

import ca.vinaitsolution.vinasign.enveloppe.application.port.JournalPreuve;
import ca.vinaitsolution.vinasign.enveloppe.domain.Ancrage;
import ca.vinaitsolution.vinasign.enveloppe.domain.ChaineScellee;
import ca.vinaitsolution.vinasign.enveloppe.domain.Enveloppe;
import ca.vinaitsolution.vinasign.enveloppe.domain.IdentifiantEnveloppe;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Intégrité du journal d'une enveloppe (VS-071) : la chaîne est recalculée maillon par maillon ; le dernier événement est
 * rattaché à l'ancrage qui le couvre, dont l'empreinte agrégée est recalculée par la base. Toute altération est détectée.
 */
public class VerifierJournal {

    /**
     * @param rupture  premier rang fautif, ou null
     * @param ancrage  ancrage qui couvre le dernier événement, ou null s'il n'est pas encore ancré
     * @param ancrageIntegre vrai si l'agrégat recalculé correspond à celui qui a été signé
     */
    public record Integrite(boolean integre, Long rupture, int evenements, Ancrage ancrage, boolean ancrageIntegre, Instant verifieeLe) {
    }

    private final ServicesEnveloppe services;
    private final JournalPreuve journal;

    public VerifierJournal(ServicesEnveloppe services, JournalPreuve journal) {
        this.services = services;
        this.journal = journal;
    }

    public Integrite executer(IdentifiantEnveloppe id) {
        Enveloppe e = services.trouver(id);
        return verifier(e.id());
    }

    /** Sans contrôle d'appelant : pour le certificat et le dossier de preuve, déjà autorisés. */
    Integrite verifier(IdentifiantEnveloppe id) {
        List<ChaineScellee.Maillon> maillons = journal.maillons(id);
        ChaineScellee.Verification v = ChaineScellee.verifier(id.valeur(), maillons);
        Optional<Ancrage> ancrage = journal.derniereConsignation(id).flatMap(journal::couvrant);
        boolean ancrageIntegre = ancrage.map(a -> journal.agreger(a.depuis(), a.jusqua()).empreinte().equals(a.empreinteAgregee())).orElse(false);
        boolean integre = v.integre() && ancrage.map(a -> ancrageIntegre).orElse(true);
        return new Integrite(integre, v.rupture().orElse(null), maillons.size(), ancrage.orElse(null), ancrageIntegre, services.horloge().instant());
    }
}
