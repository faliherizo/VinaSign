package ca.vinaitsolution.vinasign.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.PrivateKey;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Jeton d'accès de l'application (VS-012) : client credentials authentifiés par une assertion signée (private_key_jwt),
 * mis en cache jusqu'à 30 secondes de son échéance. Une seule demande à la fois.
 */
public class JetonsVinaSign {

    private static final Duration MARGE = Duration.ofSeconds(30);
    private static final ObjectMapper JSON = new ObjectMapper();

    /** Identité de l'application auprès de VinaSign. */
    public record Identite(URI serveur, String emetteur, String clientId, PrivateKey cle, String kid, List<String> portees) {

        public Identite {
            portees = List.copyOf(portees);
        }
    }

    private final Identite identite;
    private final HttpClient http;
    private final Clock horloge;
    private String jeton;
    private Instant expire = Instant.EPOCH;

    public JetonsVinaSign(Identite identite, HttpClient http, Clock horloge) {
        this.identite = identite;
        this.http = http;
        this.horloge = horloge;
    }

    public synchronized String jeton() {
        if (jeton != null && horloge.instant().isBefore(expire.minus(MARGE))) {
            return jeton;
        }
        JsonNode r = demander();
        jeton = r.get("access_token").asText();
        expire = horloge.instant().plusSeconds(r.path("expires_in").asLong(60));
        return jeton;
    }

    private JsonNode demander() {
        Instant t = horloge.instant();
        Map<String, Object> assertion = new LinkedHashMap<>();
        assertion.put("iss", identite.clientId());
        assertion.put("sub", identite.clientId());
        assertion.put("aud", identite.emetteur());
        assertion.put("jti", UUID.randomUUID().toString());
        assertion.put("iat", t.getEpochSecond());
        assertion.put("exp", t.plusSeconds(60).getEpochSecond());
        Map<String, String> formulaire = new LinkedHashMap<>();
        formulaire.put("grant_type", "client_credentials");
        formulaire.put("client_id", identite.clientId());
        formulaire.put("scope", String.join(" ", identite.portees()));
        formulaire.put("client_assertion_type", "urn:ietf:params:oauth:client-assertion-type:jwt-bearer");
        formulaire.put("client_assertion", JwtSigne.signer(identite.cle(), identite.kid(), assertion));
        HttpRequest requete = HttpRequest.newBuilder(identite.serveur().resolve("/oauth2/token")).timeout(Duration.ofSeconds(20))
                .header("Content-Type", "application/x-www-form-urlencoded").POST(HttpRequest.BodyPublishers.ofString(encoder(formulaire))).build();
        try {
            HttpResponse<String> r = http.send(requete, HttpResponse.BodyHandlers.ofString());
            if (r.statusCode() != 200) {
                throw new JetonRefuse("Jeton refusé par VinaSign (HTTP " + r.statusCode() + ") : " + r.body());
            }
            return JSON.readTree(r.body());
        } catch (IOException e) {
            throw new JetonRefuse("VinaSign injoignable au point de jeton.", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new JetonRefuse("Demande de jeton interrompue.", e);
        }
    }

    private static String encoder(Map<String, String> formulaire) {
        return formulaire.entrySet().stream().map(e -> URLEncoder.encode(e.getKey(), StandardCharsets.UTF_8) + "=" + URLEncoder.encode(e.getValue(),
                StandardCharsets.UTF_8)).collect(Collectors.joining("&"));
    }

    /** Le point de jeton a refusé l'application, ou n'a pas répondu. */
    public static class JetonRefuse extends RuntimeException {

        public JetonRefuse(String message) {
            super(message);
        }

        public JetonRefuse(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
