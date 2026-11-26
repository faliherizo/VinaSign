package ca.vinaitsolution.vinasign.integration.application;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Signature d'un webhook (VS-091) : {@code t=<secondes epoch>,v1=<HMAC-SHA256 hexadécimal de « t.corps »>}. L'horodatage
 * signé permet au destinataire de refuser un rejeu ancien ; la comparaison se fait en temps constant.
 */
public final class SignatureWebhook {

    public static final String ALGORITHME = "HmacSHA256";

    private SignatureWebhook() {
    }

    public static String signer(byte[] secret, long secondes, byte[] corps) {
        return "t=" + secondes + ",v1=" + HexFormat.of().formatHex(hmac(secret, secondes, corps));
    }

    /** Vrai si l'en-tête est bien formé, signé de ce secret, et daté d'au plus {@code toleranceSecondes} de {@code maintenant}. */
    public static boolean verifier(byte[] secret, String entete, byte[] corps, long maintenant, long toleranceSecondes) {
        if (entete == null || !entete.matches("t=\\d{1,12},v1=[0-9a-f]{64}")) {
            return false;
        }
        long t = Long.parseLong(entete.substring(2, entete.indexOf(',')));
        byte[] attendu = hmac(secret, t, corps);
        byte[] recu = HexFormat.of().parseHex(entete.substring(entete.indexOf("v1=") + 3));
        return Math.abs(maintenant - t) <= toleranceSecondes && MessageDigest.isEqual(attendu, recu);
    }

    private static byte[] hmac(byte[] secret, long secondes, byte[] corps) {
        return ca.vinaitsolution.vinasign.commun.domain.OperationCryptographique.executer("Signature du webhook", () -> {
            Mac mac = Mac.getInstance(ALGORITHME);
            mac.init(new SecretKeySpec(secret, ALGORITHME));
            mac.update((secondes + ".").getBytes(StandardCharsets.US_ASCII));
            return mac.doFinal(corps);
        });
    }
}
