package ca.vinaitsolution.vinasign.session.domain;

import java.time.Instant;
import java.util.UUID;

/** Session courte d'un signataire, ouverte par son lien (VS-040) : jeton opaque dont seule l'empreinte est conservée. */
public record SessionSignataire(UUID id, UUID organisation, UUID enveloppe, UUID signataire, String empreinteJeton, Instant expireLe) {

    public SessionSignataire {
        if (id == null || organisation == null || enveloppe == null || signataire == null || empreinteJeton == null || expireLe == null) {
            throw new IllegalArgumentException("Session incomplète.");
        }
    }

    public boolean valideA(Instant maintenant) {
        return maintenant.isBefore(expireLe);
    }
}
