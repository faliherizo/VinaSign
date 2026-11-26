package ca.vinaitsolution.vinasign.enveloppe.application.port;

import ca.vinaitsolution.vinasign.enveloppe.domain.EtatArchive;
import ca.vinaitsolution.vinasign.enveloppe.domain.IdentifiantEnveloppe;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** États archivés des documents finaux (VS-132), en ajout seul. */
public interface ArchivesDocuments {

    record ARenouveler(UUID enveloppe, UUID organisation) {
    }

    /** Toutes organisations (tâche de plateforme) : échéance avant la limite, ou document final jamais évalué. */
    List<ARenouveler> aRenouveler(Instant limite, int lot);

    void ajouter(EtatArchive etat);

    Optional<EtatArchive> dernier(IdentifiantEnveloppe enveloppe);
}
