package ca.vinaitsolution.vinasign.session.infrastructure.securite;

import ca.vinaitsolution.vinasign.session.application.port.EmpreinteCodes;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/** HMAC-SHA256 du défi et du code, avec une clé de 256 bits au moins tirée du coffre de secrets. */
public class EmpreinteHmac implements EmpreinteCodes {

    private final SecretKeySpec cle;

    public EmpreinteHmac(String cleBase64) {
        byte[] octets = cleBase64 == null ? new byte[0] : Base64.getDecoder().decode(cleBase64);
        if (octets.length < 32) {
            throw new IllegalStateException("Clé des codes absente ou trop courte : 256 bits en base64 (VINASIGN_CLE_CODES).");
        }
        this.cle = new SecretKeySpec(octets, "HmacSHA256");
    }

    @Override
    public String empreinte(UUID defi, String code) {
        return ca.vinaitsolution.vinasign.commun.domain.OperationCryptographique.executer("HMAC du code", () -> {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(cle);
            return HexFormat.of().formatHex(mac.doFinal((defi + ":" + code).getBytes(StandardCharsets.UTF_8)));
        });
    }
}
