package ca.vinaitsolution.vinasign.enveloppe.application;

import ca.vinaitsolution.vinasign.commun.application.port.GenerateurIdentifiant;
import ca.vinaitsolution.vinasign.commun.application.port.ScellementDocuments;
import ca.vinaitsolution.vinasign.enveloppe.application.port.JournalPreuve;
import ca.vinaitsolution.vinasign.enveloppe.domain.Ancrage;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;

/**
 * Ancrage du journal (VS-071), tâche horaire de la plateforme : l'empreinte agrégée des événements consignés depuis le
 * dernier ancrage est signée par le cachet et conservée en ajout seul. La fenêtre s'arrête une marge avant maintenant :
 * une transaction encore ouverte ne peut pas y consigner un événement après coup.
 */
public class AncrerJournal {

    private final JournalPreuve journal;
    private final ScellementDocuments scellement;
    private final GenerateurIdentifiant identifiants;
    private final Clock horloge;
    private final Duration marge;

    public AncrerJournal(JournalPreuve journal, ScellementDocuments scellement, GenerateurIdentifiant identifiants, Clock horloge, Duration marge) {
        this.journal = journal;
        this.scellement = scellement;
        this.identifiants = identifiants;
        this.horloge = horloge;
        this.marge = marge;
    }

    /** L'ancrage produit, ou rien si aucun événement n'a été consigné dans la fenêtre. */
    public Optional<Ancrage> executer() {
        // Précision de la base : la microseconde ; une borne plus fine serait arrondie différemment de part et d'autre.
        Instant jusqua = horloge.instant().minus(marge).truncatedTo(java.time.temporal.ChronoUnit.MICROS);
        Instant depuis = journal.dernierAncrage().map(Ancrage::jusqua).orElse(Instant.EPOCH);
        if (!jusqua.isAfter(depuis)) {
            return Optional.empty();
        }
        JournalPreuve.Agregat agregat = journal.agreger(depuis, jusqua);
        if (agregat.nombre() == 0) {
            return Optional.empty();
        }
        byte[] signature = scellement.signerEmpreinte(HexFormat.of().parseHex(agregat.empreinte()));
        Ancrage a = new Ancrage(identifiants.suivant(), depuis, jusqua, agregat.nombre(), agregat.empreinte(), signature, horloge.instant());
        journal.ajouter(a);
        return Optional.of(a);
    }
}
