package ca.vinaitsolution.vinasign.enveloppe.infrastructure.controle;

import ca.vinaitsolution.vinasign.enveloppe.application.port.ConversionPdfA;
import ca.vinaitsolution.vinasign.enveloppe.domain.DocumentRefuse;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;

/**
 * Conversion par Gotenberg (LibreOffice) en PDF/A-2b (VS-035), porté de {@code ConversionServiceHttp} du CLM. Le
 * service est déployé sans accès réseau sortant (chart Helm) : un document piégé ne peut rien appeler.
 */
public class ConversionGotenberg implements ConversionPdfA {

    private final URI adresse;
    private final Duration delai;
    private final HttpClient http = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).connectTimeout(Duration.ofSeconds(10)).build();

    public ConversionGotenberg(String url, Duration delai) {
        this.adresse = URI.create(url.replaceAll("/+$", "") + "/forms/libreoffice/convert");
        this.delai = delai;
    }

    @Override
    public byte[] convertir(byte[] document, String nomFichier) {
        String limite = "vs-" + UUID.randomUUID();
        try {
            HttpResponse<byte[]> r = http.send(HttpRequest.newBuilder(adresse).timeout(delai)
                    .header("Content-Type", "multipart/form-data; boundary=" + limite)
                    .POST(HttpRequest.BodyPublishers.ofByteArray(corps(limite, document, nomFichier))).build(), HttpResponse.BodyHandlers.ofByteArray());
            if (r.statusCode() != 200) {
                throw new DocumentRefuse("document-conversion", "Le document bureautique n'a pas pu être converti en PDF/A.");
            }
            return r.body();
        } catch (IOException e) {
            throw new IllegalStateException("Service de conversion injoignable.", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Conversion interrompue.", e);
        }
    }

    private static byte[] corps(String limite, byte[] document, String nom) throws IOException {
        ByteArrayOutputStream o = new ByteArrayOutputStream();
        o.write(("--" + limite + "\r\nContent-Disposition: form-data; name=\"files\"; filename=\"" + nom.replaceAll("[^A-Za-z0-9._-]", "_")
                + "\"\r\nContent-Type: application/octet-stream\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        o.write(document);
        o.write(("\r\n--" + limite + "\r\nContent-Disposition: form-data; name=\"pdfa\"\r\n\r\nPDF/A-2b\r\n--" + limite + "--\r\n")
                .getBytes(StandardCharsets.UTF_8));
        return o.toByteArray();
    }
}
