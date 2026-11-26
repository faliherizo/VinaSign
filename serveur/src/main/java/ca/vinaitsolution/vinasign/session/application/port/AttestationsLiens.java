package ca.vinaitsolution.vinasign.session.application.port;

import ca.vinaitsolution.vinasign.session.domain.AttestationRetenue;
import java.util.Optional;
import java.util.UUID;

/** Attestations retenues avec les sessions intégrées (VS-044) ; ajout seul. */
public interface AttestationsLiens {

    void ajouter(AttestationRetenue attestation);

    Optional<AttestationRetenue> duLien(UUID lien);
}
