package ca.vinaitsolution.vinasign.client;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.RSAPrivateKey;
import java.util.Arrays;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * JWT signé par la clé privée de l'application (RS256 pour une clé RSA, ES256 pour une clé EC P-256), sans dépendance :
 * assertion d'authentification du client (RFC 7523) et attestation d'un utilisateur (VS-044). La clé ne quitte jamais
 * l'application ; VinaSign n'en connaît que la partie publique.
 */
public final class JwtSigne {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Base64.Encoder B64 = Base64.getUrlEncoder().withoutPadding();
    private static final int TAILLE_ES256 = 32;

    private JwtSigne() {
    }

    public static String signer(PrivateKey cle, String kid, Map<String, Object> revendications) {
        String alg = algorithme(cle);
        Map<String, Object> entete = new LinkedHashMap<>();
        entete.put("alg", alg);
        entete.put("typ", "JWT");
        if (kid != null) {
            entete.put("kid", kid);
        }
        String signe = B64.encodeToString(json(entete)) + "." + B64.encodeToString(json(revendications));
        try {
            Signature s = Signature.getInstance("RS256".equals(alg) ? "SHA256withRSA" : "SHA256withECDSA");
            s.initSign(cle);
            s.update(signe.getBytes(StandardCharsets.US_ASCII));
            byte[] signature = s.sign();
            return signe + "." + B64.encodeToString("RS256".equals(alg) ? signature : joseDepuisDer(signature));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Signature du JWT impossible.", e);
        }
    }

    static String algorithme(PrivateKey cle) {
        if (cle instanceof RSAPrivateKey) {
            return "RS256";
        }
        if (cle instanceof ECPrivateKey ec && ec.getParams().getCurve().getField().getFieldSize() == 256) {
            return "ES256";
        }
        throw new IllegalArgumentException("Clé refusée : RSA (RS256) ou EC P-256 (ES256).");
    }

    /** Signature ECDSA DER (SEQUENCE de deux INTEGER) → concaténation R‖S de 32 octets chacun (RFC 7518, § 3.4). */
    static byte[] joseDepuisDer(byte[] der) {
        int i = der[1] < 0 ? 2 + (der[1] & 0x7F) : 2;
        byte[] r = entier(der, i);
        byte[] s = entier(der, i + 2 + der[i + 1]);
        byte[] jose = new byte[2 * TAILLE_ES256];
        System.arraycopy(r, 0, jose, TAILLE_ES256 - r.length, r.length);
        System.arraycopy(s, 0, jose, 2 * TAILLE_ES256 - s.length, s.length);
        return jose;
    }

    private static byte[] entier(byte[] der, int position) {
        int longueur = der[position + 1];
        byte[] valeur = Arrays.copyOfRange(der, position + 2, position + 2 + longueur);
        int debut = 0;
        while (debut < valeur.length - 1 && valeur[debut] == 0) {
            debut++;
        }
        return Arrays.copyOfRange(valeur, debut, valeur.length);
    }

    private static byte[] json(Object valeur) {
        try {
            return JSON.writeValueAsBytes(valeur);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Revendications non sérialisables.", e);
        }
    }
}
