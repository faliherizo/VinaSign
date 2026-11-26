package ca.vinaitsolution.vinasign.support;

import java.time.Duration;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistrar;
import org.testcontainers.azure.AzuriteContainer;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;

/**
 * Stockage objet (Azurite), antivirus (ClamAV), conversion (Gotenberg) et validateur PDF/A (veraPDF) réels pour les tests d'intégration des documents, partagés entre
 * contextes : jamais un double en mémoire à la place d'une dépendance d'exploitation.
 */
@TestConfiguration(proxyBeanMethods = false)
public class StockageDeTest {

    public static final AzuriteContainer AZURITE = new AzuriteContainer("mcr.microsoft.com/azure-storage/azurite:3.33.0");
    @SuppressWarnings("resource")
    // Docker Desktop accepte les connexions sur le port publié avant que clamd écoute (il met d'abord ses signatures à
    // jour) : on attend son annonce de démarrage, pas le port.
    public static final GenericContainer<?> CLAMAV = new GenericContainer<>("clamav/clamav:1.4").withExposedPorts(3310)
            .waitingFor(Wait.forLogMessage(".*clamd started.*", 1).withStartupTimeout(Duration.ofMinutes(5)));

    @SuppressWarnings("resource")
    public static final GenericContainer<?> GOTENBERG = new GenericContainer<>("gotenberg/gotenberg:8.17.3").withExposedPorts(3000)
            .waitingFor(Wait.forHttp("/health").forPort(3000).withStartupTimeout(Duration.ofMinutes(3)));
    @SuppressWarnings("resource")
    public static final GenericContainer<?> VERAPDF = new GenericContainer<>("verapdf/rest:latest").withExposedPorts(8080)
            .waitingFor(Wait.forListeningPort().withStartupTimeout(Duration.ofMinutes(3)));

    static {
        AZURITE.start();
        CLAMAV.start();
        GOTENBERG.start();
        VERAPDF.start();
    }

    public static String conversion() {
        return "http://" + GOTENBERG.getHost() + ":" + GOTENBERG.getMappedPort(3000);
    }

    /** Rapport JSON de veraPDF (profil PDF/A-2b) pour ce PDF ; « compliant » dit s'il est conforme. */
    public static String rapportPdfA2b(byte[] pdf) throws Exception {
        var http = java.net.http.HttpClient.newHttpClient();
        String limite = "vs-essai";
        var corps = new java.io.ByteArrayOutputStream();
        corps.write(("--" + limite + "\r\nContent-Disposition: form-data; name=\"file\"; filename=\"d.pdf\"\r\nContent-Type: application/pdf\r\n\r\n")
                .getBytes(java.nio.charset.StandardCharsets.UTF_8));
        corps.write(pdf);
        corps.write(("\r\n--" + limite + "--\r\n").getBytes(java.nio.charset.StandardCharsets.UTF_8));
        return http.send(java.net.http.HttpRequest.newBuilder(java.net.URI.create("http://" + VERAPDF.getHost() + ":" + VERAPDF.getMappedPort(8080)
                        + "/api/validate/2b")).header("Accept", "application/json").header("Content-Type", "multipart/form-data; boundary=" + limite)
                .POST(java.net.http.HttpRequest.BodyPublishers.ofByteArray(corps.toByteArray())).build(), java.net.http.HttpResponse.BodyHandlers.ofString()).body();
    }

    public static boolean conformePdfA2b(String rapport) {
        return rapport.contains("\"compliant\" : true") || rapport.contains("\"compliant\":true");
    }

    @Bean
    DynamicPropertyRegistrar stockageEtAntivirus() {
        return r -> {
            r.add("vinasign.enveloppes.stockage.connexion", AZURITE::getConnectionString);
            r.add("vinasign.enveloppes.stockage.creer-conteneur", () -> true);
            r.add("vinasign.enveloppes.antivirus.hote", CLAMAV::getHost);
            r.add("vinasign.enveloppes.antivirus.port", () -> CLAMAV.getMappedPort(3310));
            r.add("vinasign.enveloppes.conversion.url", StockageDeTest::conversion);
        };
    }
}
