package ca.vinaitsolution.vinasign.session.infrastructure.identite;

import ca.vinaitsolution.vinasign.commun.domain.OperationCryptographique;
import ca.vinaitsolution.vinasign.session.application.port.FournisseurIdentite;
import ca.vinaitsolution.vinasign.session.domain.VerificationIdentiteRefusee;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Fournisseur générique (VS-131), en attendant le choix d'un fournisseur réel : le signataire est envoyé à une adresse
 * configurée ({@code {reference}} et {@code {langue}} y sont substitués) ; le fournisseur rappelle avec un corps JSON
 * {@code {"reference", "resultat": "reussie"|"echouee", "methode", "assurance"}} signé HMAC-SHA256 par un secret partagé,
 * dans l'en-tête {@code X-VinaSign-Signature: sha256=<hex>}. Comparaison en temps constant.
 */
public class FournisseurIdentiteGenerique implements FournisseurIdentite {

    private static final String PREFIXE = "sha256=";

    private final String code;
    private final String url;
    private final byte[] secret;
    private final ObjectMapper json;

    public FournisseurIdentiteGenerique(String code, String url, String secret, ObjectMapper json) {
        if (url == null || !url.startsWith("https://") || !url.contains("{reference}")) {
            throw new IllegalStateException("Adresse du fournisseur d'identité : https://…{reference}… attendue.");
        }
        if (secret == null || secret.length() < 32) {
            throw new IllegalStateException("Secret du fournisseur d'identité : 32 caractères au moins (environnement ou coffre).");
        }
        this.code = code;
        this.url = url;
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
        this.json = json;
    }

    @Override
    public String code() {
        return code;
    }

    @Override
    public String demarrer(Demande d) {
        return url.replace("{reference}", URLEncoder.encode(d.reference(), StandardCharsets.UTF_8))
                .replace("{langue}", URLEncoder.encode(d.langue(), StandardCharsets.UTF_8));
    }

    @Override
    public Conclusion conclure(byte[] corps, String signature) {
        if (signature == null || !signature.startsWith(PREFIXE) || !MessageDigest.isEqual(hmac(corps),
                HexFormat.of().parseHex(hexOuVide(signature.substring(PREFIXE.length()))))) {
            throw new VerificationIdentiteRefusee("Signature du rappel invalide.");
        }
        try {
            JsonNode n = json.readTree(corps);
            String reference = n.path("reference").asText("");
            String resultat = n.path("resultat").asText("");
            if (reference.isEmpty() || !(resultat.equals("reussie") || resultat.equals("echouee"))) {
                throw new VerificationIdentiteRefusee("Rappel incomplet : reference et resultat (reussie ou echouee) attendus.");
            }
            return new Conclusion(reference, resultat.equals("reussie"), texte(n, "methode"), texte(n, "assurance"));
        } catch (IOException e) {
            throw new VerificationIdentiteRefusee("Rappel illisible.");
        }
    }

    private static String texte(JsonNode n, String champ) {
        String v = n.path(champ).asText("");
        return v.isEmpty() ? null : v.substring(0, Math.min(v.length(), 60));
    }

    private static String hexOuVide(String hex) {
        return hex.matches("[0-9a-fA-F]{64}") ? hex : "";
    }

    private byte[] hmac(byte[] corps) {
        return OperationCryptographique.executer("HMAC du rappel d'identité", () -> {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            return mac.doFinal(corps);
        });
    }
}
