package ca.vinaitsolution.vinasign.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistrar;
import org.testcontainers.containers.GenericContainer;

/** Relais SMTP réel (Mailpit) pour les tests des courriels : on lit ce que le destinataire recevrait. */
@TestConfiguration(proxyBeanMethods = false)
public class CourrielDeTest {

    @SuppressWarnings("resource")
    public static final GenericContainer<?> MAILPIT = new GenericContainer<>("axllent/mailpit:v1.21.8").withExposedPorts(1025, 8025);

    static {
        MAILPIT.start();
    }

    @Bean
    DynamicPropertyRegistrar relaisSmtp() {
        return r -> {
            r.add("spring.mail.host", MAILPIT::getHost);
            r.add("spring.mail.port", () -> MAILPIT.getMappedPort(1025));
        };
    }

    /** Messages reçus par ce destinataire (API de Mailpit). */
    public static JsonNode recus(String destinataire) throws Exception {
        String url = "http://" + MAILPIT.getHost() + ":" + MAILPIT.getMappedPort(8025) + "/api/v1/search?query="
                + java.net.URLEncoder.encode("to:" + destinataire, java.nio.charset.StandardCharsets.UTF_8);
        return new ObjectMapper().readTree(HttpClient.newHttpClient().send(HttpRequest.newBuilder(URI.create(url)).build(),
                HttpResponse.BodyHandlers.ofString()).body()).get("messages");
    }

    /** Message complet (texte, pièces jointes). */
    public static JsonNode message(String id) throws Exception {
        String url = "http://" + MAILPIT.getHost() + ":" + MAILPIT.getMappedPort(8025) + "/api/v1/message/" + id;
        return new ObjectMapper().readTree(HttpClient.newHttpClient().send(HttpRequest.newBuilder(URI.create(url)).build(),
                HttpResponse.BodyHandlers.ofString()).body());
    }
}
