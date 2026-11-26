package ca.vinaitsolution.vinasign.commun.infrastructure;

import ca.vinaitsolution.vinasign.commun.application.port.Chiffrement;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * AES-256-GCM : vecteur d'initialisation aléatoire de 96 bits en tête du chiffré, étiquette de 128 bits. Une altération
 * du chiffré est détectée au déchiffrement. Clé tirée du coffre de secrets (VINASIGN_CLE_CHIFFREMENT) ; la clé de
 * données par organisation viendra avec VS-133.
 */
public class ChiffrementAesGcm implements Chiffrement {

    private static final int IV = 12;
    private final SecretKeySpec cle;
    private final SecureRandom alea;

    public ChiffrementAesGcm(String cleBase64, SecureRandom alea) {
        byte[] octets = cleBase64 == null ? new byte[0] : Base64.getDecoder().decode(cleBase64);
        if (octets.length != 32) {
            throw new IllegalStateException("Clé de chiffrement absente ou invalide : 256 bits en base64 (VINASIGN_CLE_CHIFFREMENT).");
        }
        this.cle = new SecretKeySpec(octets, "AES");
        this.alea = alea;
    }

    @Override
    public byte[] chiffrer(String clair) {
        return ca.vinaitsolution.vinasign.commun.domain.OperationCryptographique.executer("Chiffrement", () -> {
            byte[] iv = new byte[IV];
            alea.nextBytes(iv);
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.ENCRYPT_MODE, cle, new GCMParameterSpec(128, iv));
            byte[] chiffre = c.doFinal(clair.getBytes(StandardCharsets.UTF_8));
            return ByteBuffer.allocate(IV + chiffre.length).put(iv).put(chiffre).array();
        });
    }

    @Override
    public String dechiffrer(byte[] chiffre) {
        try {
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.DECRYPT_MODE, cle, new GCMParameterSpec(128, chiffre, 0, IV));
            return new String(c.doFinal(chiffre, IV, chiffre.length - IV), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            throw new IllegalStateException("Déchiffrement impossible : clé différente ou donnée altérée.", e);
        }
    }
}
