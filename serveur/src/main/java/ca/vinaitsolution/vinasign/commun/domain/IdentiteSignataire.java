package ca.vinaitsolution.vinasign.commun.domain;

import java.util.UUID;

/** Signataire authentifié par sa session : son organisation, son enveloppe, lui-même, sa session. */
public record IdentiteSignataire(IdentifiantOrganisation organisation, UUID enveloppe, UUID signataire, UUID session) {

    public IdentiteSignataire {
        if (organisation == null || enveloppe == null || signataire == null || session == null) {
            throw new IllegalArgumentException("Identité de signataire incomplète.");
        }
    }
}
