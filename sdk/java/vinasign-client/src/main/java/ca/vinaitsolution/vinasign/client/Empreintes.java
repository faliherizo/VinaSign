package ca.vinaitsolution.vinasign.client;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.Normalizer;

/**
 * Empreintes SHA-256 à faire signer en mode empreinte (VS-034) : le document ne quitte pas l'application. Pour un texte
 * (note clinique), une forme canonique stable : Unicode NFC, fins de ligne « \n », sans espaces de fin de ligne ni lignes
 * vides finales ; la même note donne la même empreinte quel que soit le poste qui l'a saisie.
 */
public final class Empreintes {

    private Empreintes() {
    }

    public static byte[] sha256(byte[] contenu) {
        return condenser("SHA-256", contenu);
    }

    /** L'indisponibilité d'un algorithme devient une erreur non contrôlée. */
    static byte[] condenser(String algorithme, byte[] contenu) {
        try {
            return MessageDigest.getInstance(algorithme).digest(contenu);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(algorithme + " indisponible", e);
        }
    }

    public static String canonique(String texte) {
        String nfc = Normalizer.normalize(texte, Normalizer.Form.NFC).replace("\r\n", "\n").replace('\r', '\n');
        return nfc.replaceAll("[ \\t]+\\n", "\n").replaceAll("[ \\t]+$", "").replaceAll("\\n+$", "");
    }

    public static byte[] texte(String texte) {
        return sha256(canonique(texte).getBytes(StandardCharsets.UTF_8));
    }
}
