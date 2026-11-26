package ca.vinaitsolution.vinasign.commun.domain;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Empreintes SHA-256 de la plateforme (jetons, documents, corps idempotents, cachet), en un seul endroit : l'algorithme est
 * toujours présent dans un JDK conforme ; son absence devient une erreur non contrôlée plutôt qu'une exception propagée.
 */
public final class Condensats {

    private Condensats() {
    }

    public static byte[] sha256(byte[] octets) {
        return condenser("SHA-256", octets);
    }

    public static String sha256Hex(byte[] octets) {
        return HexFormat.of().formatHex(sha256(octets));
    }

    public static String sha256Hex(String texte) {
        return sha256Hex(texte.getBytes(StandardCharsets.UTF_8));
    }

    static byte[] condenser(String algorithme, byte[] octets) {
        try {
            return MessageDigest.getInstance(algorithme).digest(octets);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(algorithme + " indisponible", e);
        }
    }
}
