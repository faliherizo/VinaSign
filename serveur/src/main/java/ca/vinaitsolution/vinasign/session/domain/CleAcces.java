package ca.vinaitsolution.vinasign.session.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * Clé d'accès (WebAuthn) d'un signataire récurrent (VS-043) : rattachée à son identité dans l'organisation, jamais à une
 * enveloppe. {@code donnees} : la clé publique et ses attributs tels que l'authentificateur les a attestés.
 */
public record CleAcces(UUID id, String identite, String credentialId, byte[] donnees, long compteur, Instant creeLe) {

    public CleAcces {
        if (id == null || identite == null || credentialId == null || credentialId.isBlank() || donnees == null || compteur < 0 || creeLe == null) {
            throw new IllegalArgumentException("Clé d'accès incomplète.");
        }
        donnees = donnees.clone();
    }

    @Override
    public byte[] donnees() {
        return donnees.clone();
    }
}
