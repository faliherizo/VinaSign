package ca.vinaitsolution.vinasign.client;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Vérification d'un webhook de VinaSign (VS-091) : en-tête {@code VinaSign-Signature: t=<secondes>,v1=<HMAC-SHA256 de
 * « t.corps »>}, comparé en temps constant, refusé au-delà de la tolérance (rejeu). Ensuite : dédupliquer par
 * {@code VinaSign-Event-Id}, puis relire l'enveloppe par l'API plutôt que de se fier au seul webhook.
 */
public final class VinaSignWebhooks {

    public static final String ENTETE_SIGNATURE = "VinaSign-Signature";
    public static final String ENTETE_EVENEMENT = "VinaSign-Event-Id";

    private final byte[] secret;
    private final Clock horloge;
    private final Duration tolerance;

    public VinaSignWebhooks(String secret, Clock horloge, Duration tolerance) {
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
        this.horloge = horloge;
        this.tolerance = tolerance;
    }

    public boolean verifier(String entete, byte[] corps) {
        if (entete == null || !entete.matches("t=\\d{1,12},v1=[0-9a-f]{64}")) {
            return false;
        }
        long t = Long.parseLong(entete.substring(2, entete.indexOf(',')));
        byte[] recu = HexFormat.of().parseHex(entete.substring(entete.indexOf("v1=") + 3));
        return Math.abs(horloge.instant().getEpochSecond() - t) <= tolerance.toSeconds()
                && MessageDigest.isEqual(hmac("HmacSHA256", secret, t, corps), recu);
    }

    static byte[] hmac(String algorithme, byte[] secret, long t, byte[] corps) {
        try {
            Mac mac = Mac.getInstance(algorithme);
            mac.init(new SecretKeySpec(secret, algorithme));
            mac.update((t + ".").getBytes(StandardCharsets.US_ASCII));
            return mac.doFinal(corps);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(algorithme + " indisponible", e);
        }
    }
}
