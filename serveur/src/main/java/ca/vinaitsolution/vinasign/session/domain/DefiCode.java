package ca.vinaitsolution.vinasign.session.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * Code à usage unique envoyé au signataire pour le niveau renforcé (VS-042), porté du portail du CLM. Seule l'empreinte
 * HMAC du code est conservée. Cinq essais au plus, puis verrouillage ; un code validé ne resert pas ; un code expiré
 * est refusé. Les refus ne disent jamais si un autre code aurait été le bon.
 */
public record DefiCode(UUID id, UUID session, String canal, String empreinteCode, Instant expireLe, int essais, int essaisMax,
                       Instant valideLe) {

    public DefiCode {
        if (id == null || session == null || canal == null || canal.isBlank() || empreinteCode == null || expireLe == null) {
            throw new IllegalArgumentException("Défi incomplet.");
        }
        if (essaisMax < 1 || essais < 0) {
            throw new IllegalArgumentException("Nombre d'essais invalide.");
        }
    }

    public static DefiCode emettre(UUID id, UUID session, String canal, String empreinteCode, Instant expireLe, int essaisMax) {
        return new DefiCode(id, session, canal, empreinteCode, expireLe, 0, essaisMax, null);
    }

    public boolean estVerrouille() {
        return essais >= essaisMax;
    }

    /**
     * Tentative avec l'empreinte du code saisi. Rend le défi suivant : validé si l'empreinte concorde, un essai de plus
     * sinon. Refusée si le défi est déjà validé, verrouillé ou expiré.
     */
    public DefiCode tenter(String empreinteSaisie, Instant maintenant) {
        if (valideLe != null) {
            throw new CodeRefuse("code-deja-utilise", "Ce code a déjà servi. Demander un nouveau code.");
        }
        if (estVerrouille()) {
            throw new CodeRefuse("code-verrouille", "Trop d'essais : demander un nouveau code.");
        }
        if (!maintenant.isBefore(expireLe)) {
            throw new CodeRefuse("code-expire", "Ce code a expiré : demander un nouveau code.");
        }
        if (java.security.MessageDigest.isEqual(empreinteCode.getBytes(java.nio.charset.StandardCharsets.US_ASCII),
                empreinteSaisie.getBytes(java.nio.charset.StandardCharsets.US_ASCII))) {
            return new DefiCode(id, session, canal, empreinteCode, expireLe, essais, essaisMax, maintenant);
        }
        return new DefiCode(id, session, canal, empreinteCode, expireLe, essais + 1, essaisMax, null);
    }

    public int essaisRestants() {
        return Math.max(0, essaisMax - essais);
    }
}
