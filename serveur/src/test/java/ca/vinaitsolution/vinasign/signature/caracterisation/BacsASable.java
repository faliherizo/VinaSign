package ca.vinaitsolution.vinasign.signature.caracterisation;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;

/**
 * Services externes réels de infra/bacs-a-sable (VS-ADR-002) : ICP et horodatage, pyHanko et OpenSSL, veraPDF,
 * conversion. Ports tirés de l'environnement comme dans le docker-compose. Avec {@code -Dvinasign.bacs-a-sable=obligatoires}
 * (profil Maven caracterisation), un bac à sable absent fait échouer la suite au lieu de la sauter.
 */
public final class BacsASable {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    /** État de l'ICP (magasin du cachet, mot de passe) : lu en place, jamais copié. */
    static final Path ETAT_ICP = Path.of(System.getProperty("vinasign.bacs-a-sable.etat", "../infra/bacs-a-sable/icp/etat"));

    private BacsASable() {
    }

    static String url(String variable, int defaut) {
        return "http://localhost:" + System.getenv().getOrDefault(variable, String.valueOf(defaut));
    }

    static String icp() {
        return url("VS_ICP_PORT", 28090);
    }

    static String validation() {
        return url("VS_VALIDATION_PORT", 28093);
    }

    static String verapdf() {
        return url("VS_VERAPDF_PORT", 28092);
    }

    static String conversion() {
        return url("VS_CONVERSION_PORT", 28030);
    }

    /** Saute la suite si les bacs à sable sont absents, sauf si on les a déclarés obligatoires. */
    static void exiger() {
        boolean presents = repond(icp() + "/sante") && repond(validation() + "/sante") && Files.isRegularFile(ETAT_ICP.resolve("cachet.p12"));
        if ("obligatoires".equals(System.getProperty("vinasign.bacs-a-sable"))) {
            if (!presents) {
                throw new IllegalStateException("Bacs à sable requis : docker compose -f infra/bacs-a-sable/docker-compose.yml up -d --wait");
            }
            return;
        }
        assumeTrue(presents, "bacs à sable absents");
    }

    private static boolean repond(String url) {
        try {
            return HTTP.send(HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(3)).build(),
                    HttpResponse.BodyHandlers.discarding()).statusCode() == 200;
        } catch (IOException e) {
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    /** Verdict de pyHanko (racine de l'ICP comme seule ancre, révocation exigée) pour un profil pades ou pades-lta. */
    static JsonNode pyhanko(byte[] pdf, String profil) throws Exception {
        return json(HTTP.send(HttpRequest.newBuilder(URI.create(validation() + "/valider?profil=" + profil))
                .header("Content-Type", "application/pdf").timeout(Duration.ofMinutes(2))
                .POST(HttpRequest.BodyPublishers.ofByteArray(pdf)).build(), HttpResponse.BodyHandlers.ofByteArray()).body());
    }

    /** Verdict de {@code openssl cms -verify} pour une signature détachée et son contenu. */
    static JsonNode opensslCms(byte[] signature, byte[] contenu) throws Exception {
        String corps = JSON.writeValueAsString(Map.of("signature", Base64.getEncoder().encodeToString(signature),
                "contenu", Base64.getEncoder().encodeToString(contenu)));
        return json(HTTP.send(HttpRequest.newBuilder(URI.create(validation() + "/cms")).header("Content-Type", "application/json")
                .timeout(Duration.ofMinutes(1)).POST(HttpRequest.BodyPublishers.ofString(corps)).build(),
                HttpResponse.BodyHandlers.ofByteArray()).body());
    }

    /** Rapport veraPDF (profil PDF/A-2b). */
    static JsonNode verapdf(byte[] pdf) throws Exception {
        Multipart m = new Multipart().fichier("file", "document.pdf", "application/pdf", pdf);
        return json(HTTP.send(HttpRequest.newBuilder(URI.create(verapdf() + "/api/validate/2b")).header("Accept", "application/json")
                .header("Content-Type", m.type()).timeout(Duration.ofMinutes(2)).POST(m.corps()).build(),
                HttpResponse.BodyHandlers.ofByteArray()).body());
    }

    /** Document PDF/A-2b produit par le service de conversion à partir d'un HTML. */
    static byte[] pdfA(String html) throws Exception {
        Multipart m = new Multipart().fichier("files", "index.html", "text/html", html.getBytes(StandardCharsets.UTF_8))
                .champ("pdfa", "PDF/A-2b");
        HttpResponse<byte[]> r = HTTP.send(HttpRequest.newBuilder(URI.create(conversion() + "/forms/chromium/convert/html"))
                .header("Content-Type", m.type()).timeout(Duration.ofMinutes(2)).POST(m.corps()).build(), HttpResponse.BodyHandlers.ofByteArray());
        if (r.statusCode() != 200) {
            throw new IllegalStateException("Conversion PDF/A refusée : " + r.statusCode());
        }
        return r.body();
    }

    static Path magasinCachet() {
        return ETAT_ICP.resolve("cachet.p12");
    }

    static String motDePasseCachet() throws IOException {
        return Files.readString(ETAT_ICP.resolve("mot-de-passe.txt")).strip();
    }

    private static JsonNode json(byte[] corps) throws IOException {
        return JSON.readTree(corps);
    }

    /** Corps multipart/form-data minimal. */
    private static final class Multipart {
        private final String limite = "vs-" + UUID.randomUUID();
        private final ByteArrayOutputStream octets = new ByteArrayOutputStream();

        Multipart fichier(String nom, String fichier, String type, byte[] contenu) throws IOException {
            octets.write(("--" + limite + "\r\nContent-Disposition: form-data; name=\"" + nom + "\"; filename=\"" + fichier
                    + "\"\r\nContent-Type: " + type + "\r\n\r\n").getBytes(StandardCharsets.UTF_8));
            octets.write(contenu);
            octets.write("\r\n".getBytes(StandardCharsets.UTF_8));
            return this;
        }

        Multipart champ(String nom, String valeur) throws IOException {
            octets.write(("--" + limite + "\r\nContent-Disposition: form-data; name=\"" + nom + "\"\r\n\r\n" + valeur + "\r\n")
                    .getBytes(StandardCharsets.UTF_8));
            return this;
        }

        String type() {
            return "multipart/form-data; boundary=" + limite;
        }

        HttpRequest.BodyPublisher corps() {
            byte[] fin = ("--" + limite + "--\r\n").getBytes(StandardCharsets.UTF_8);
            byte[] tout = new byte[octets.size() + fin.length];
            System.arraycopy(octets.toByteArray(), 0, tout, 0, octets.size());
            System.arraycopy(fin, 0, tout, octets.size(), fin.length);
            return HttpRequest.BodyPublishers.ofByteArray(tout);
        }
    }
}
