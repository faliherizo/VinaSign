package ca.vinaitsolution.vinasign.session.domain;

import java.time.Instant;
import java.util.UUID;

/** Défi d'une cérémonie WebAuthn (VS-043) : lié à la session, d'une nature (enregistrement ou authentification), à usage unique. */
public record DefiCleAcces(UUID session, String nature, byte[] defi, Instant expireLe) {

    public static final String ENREGISTREMENT = "enregistrement";
    public static final String AUTHENTIFICATION = "authentification";

    public DefiCleAcces {
        if (session == null || nature == null || defi == null || defi.length < 16 || expireLe == null) {
            throw new IllegalArgumentException("Défi de clé d'accès incomplet.");
        }
        defi = defi.clone();
    }

    @Override
    public byte[] defi() {
        return defi.clone();
    }

    /** Le défi attendu pour cette nature de cérémonie, encore valide à cette heure ; {@link CleAccesRefusee} sinon. */
    public byte[] exiger(String attendue, Instant maintenant) {
        if (!nature.equals(attendue) || !maintenant.isBefore(expireLe)) {
            throw new CleAccesRefusee("Défi absent, expiré ou d'une autre cérémonie : recommencer.");
        }
        return defi();
    }
}
