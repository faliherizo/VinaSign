package ca.vinaitsolution.vinasign.session.domain;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Attestation retenue avec une session intégrée (VS-044) : qui l'application a authentifié, comment et quand, et le
 * niveau accordé en conséquence. Elle entre dans la preuve ; on ne la déduit jamais a posteriori (VS-ADR-007).
 */
public record AttestationRetenue(UUID lien, String sujet, List<String> methodes, Instant authentifieLe, String niveau, Instant attesteLe) {

    public AttestationRetenue {
        if (lien == null || sujet == null || niveau == null || attesteLe == null) {
            throw new IllegalArgumentException("Attestation retenue incomplète.");
        }
        methodes = List.copyOf(methodes);
    }
}
