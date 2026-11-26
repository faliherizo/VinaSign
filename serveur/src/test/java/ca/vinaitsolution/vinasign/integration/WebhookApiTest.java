package ca.vinaitsolution.vinasign.integration;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

import ca.vinaitsolution.vinasign.integration.application.LivrerWebhooks;
import ca.vinaitsolution.vinasign.integration.application.SignatureWebhook;
import ca.vinaitsolution.vinasign.support.ApplicationsDeTest;
import ca.vinaitsolution.vinasign.support.ApplicationsDeTest.Application;
import ca.vinaitsolution.vinasign.support.PostgresDeTest;
import ca.vinaitsolution.vinasign.support.ReferentielsSql;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.liquibase.LiquibaseDataSource;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/** VS-091 — webhooks signés, livrés à un vrai récepteur HTTP, repris en cas d'échec, tracés ; destinations gardées (SSRF). */
@SpringBootTest
@AutoConfigureMockMvc
@Import({PostgresDeTest.class, ApplicationsDeTest.class, ReferentielsSql.class})
class WebhookApiTest {

    private static final List<String> PORTEES = List.of("enveloppes-creer", "enveloppes-lire", "enveloppes-envoyer", "signataires-gerer", "webhooks-gerer");

    /** Récepteur : enregistre chaque requête, répond le code demandé. */
    record Recue(Map<String, String> entetes, byte[] corps) {
    }

    private static final List<Recue> RECUES = new CopyOnWriteArrayList<>();
    private static final AtomicInteger REPONSE = new AtomicInteger(200);
    private static final HttpServer RECEPTEUR = recepteur();

    private static HttpServer recepteur() {
        try {
            HttpServer s = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
            s.createContext("/recepteur", e -> {
                RECUES.add(new Recue(Map.of("signature", String.valueOf(e.getRequestHeaders().getFirst("VinaSign-Signature")),
                        "evenement", String.valueOf(e.getRequestHeaders().getFirst("VinaSign-Event-Id")),
                        "type", String.valueOf(e.getRequestHeaders().getFirst("VinaSign-Event-Type"))), e.getRequestBody().readAllBytes()));
                e.sendResponseHeaders(REPONSE.get(), -1);
                e.close();
            });
            s.start();
            return s;
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @DynamicPropertySource
    static void destinationAutorisee(DynamicPropertyRegistry r) {
        r.add("vinasign.webhooks.autorises", () -> "localhost:" + RECEPTEUR.getAddress().getPort());
    }

    @AfterAll
    static void arreter() {
        RECEPTEUR.stop(0);
    }

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired ApplicationsDeTest applications;
    @Autowired LivrerWebhooks livraison;
    @Autowired @LiquibaseDataSource DataSource proprietaire;

    private Application app;

    @BeforeEach
    void preparer() {
        app = applications.enregistrer(PORTEES.toArray(String[]::new));
        RECUES.clear();
        REPONSE.set(200);
    }

    private JwtRequestPostProcessor jeton() {
        return jwt().jwt(j -> j.subject(app.clientId()).claim("org", app.organisation().toString()).claim("client_id", app.clientId()))
                .authorities(PORTEES.stream().<GrantedAuthority>map(p -> new SimpleGrantedAuthority("SCOPE_" + p)).toList());
    }

    private MockHttpServletResponse appel(HttpMethod m, String url, Object corps) throws Exception {
        MockHttpServletRequestBuilder r = MockMvcRequestBuilders.request(m, url).with(jeton()).header("Idempotency-Key", UUID.randomUUID().toString());
        if (corps != null) r = r.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(corps));
        return mvc.perform(r).andReturn().getResponse();
    }

    private JsonNode corps(MockHttpServletResponse r) throws Exception {
        return json.readTree(r.getContentAsString(StandardCharsets.UTF_8));
    }

    private String code(String referentiel, String code) throws Exception {
        for (JsonNode v : corps(mvc.perform(MockMvcRequestBuilders.get("/api/v1/referentiels/" + referentiel)).andReturn().getResponse()).get("valeurs")) {
            if (v.get("code").asText().equals(code)) return code;
        }
        throw new IllegalStateException(referentiel);
    }

    private String envoyee() throws Exception {
        String id = corps(appel(HttpMethod.POST, "/api/v1/envelopes", Map.of("externalReference", "W-" + UUID.randomUUID(), "mode", code("mode-enveloppe", "empreinte"),
                "expiresAt", Instant.now().plusSeconds(86400).toString()))).get("id").asText();
        appel(HttpMethod.PUT, "/api/v1/envelopes/" + id + "/signers", List.of(Map.of("nom", "Alice", "courriel", "a@exemple.ca", "langue", code("langue", "fr"),
                "type", code("type-signataire", "invite"), "ordre", 1)));
        assertEquals(200, appel(HttpMethod.POST, "/api/v1/envelopes/" + id + "/send", null).getStatus());
        return id;
    }

    private String url() {
        return "http://localhost:" + RECEPTEUR.getAddress().getPort() + "/recepteur";
    }

    @Test
    void l_evenement_est_livre_signe_et_trace_puis_la_destination_est_retiree() throws Exception {
        MockHttpServletResponse creee = appel(HttpMethod.POST, "/api/v1/webhooks", Map.of("url", url(), "evenements", List.of("envelope.sent")));
        assertEquals(201, creee.getStatus(), creee.getContentAsString());
        String secret = corps(creee).get("secret").asText();
        String destination = corps(creee).get("id").asText();
        assertTrue(secret.length() >= 43, "256 bits");
        assertFalse(corps(appel(HttpMethod.GET, "/api/v1/webhooks", null)).toString().contains(secret), "le secret n'est jamais rendu de nouveau");
        assertFalse(new JdbcTemplate(proprietaire).queryForObject("SELECT secret_chiffre FROM webhook_destination WHERE id = ?::uuid", String.class, destination)
                .contains(secret), "conservé chiffré");

        String id = envoyee();
        assertTrue(livraison.executer() >= 1);
        Recue r = RECUES.stream().filter(x -> new String(x.corps(), StandardCharsets.UTF_8).contains(id)).findFirst().orElseThrow();
        assertEquals("envelope.sent", r.entetes().get("type"));
        assertTrue(SignatureWebhook.verifier(secret.getBytes(StandardCharsets.UTF_8), r.entetes().get("signature"), r.corps(),
                Instant.now().getEpochSecond(), 300), "signature HMAC horodatée vérifiable");
        JsonNode evenement = json.readTree(r.corps());
        assertEquals(r.entetes().get("evenement"), evenement.get("id").asText(), "identifiant d'événement : déduplication par le destinataire");
        assertFalse(new String(r.corps(), StandardCharsets.UTF_8).contains("a@exemple.ca"), "aucun renseignement personnel");
        JsonNode trace = corps(appel(HttpMethod.GET, "/api/v1/webhooks/" + destination + "/deliveries", null));
        assertEquals(200, trace.get(0).get("dernierCode").asInt());
        assertTrue(trace.get(0).hasNonNull("livreLe"));

        assertEquals(204, appel(HttpMethod.DELETE, "/api/v1/webhooks/" + destination, null).getStatus());
        assertEquals(0, corps(appel(HttpMethod.GET, "/api/v1/webhooks", null)).size());
        assertEquals(404, appel(HttpMethod.DELETE, "/api/v1/webhooks/" + destination, null).getStatus());
    }

    @Test
    void un_refus_du_destinataire_est_repris_plus_tard_et_trace() throws Exception {
        String destination = corps(appel(HttpMethod.POST, "/api/v1/webhooks", Map.of("url", url()))).get("id").asText();
        REPONSE.set(503);
        envoyee();
        livraison.executer();
        JsonNode trace = corps(appel(HttpMethod.GET, "/api/v1/webhooks/" + destination + "/deliveries", null));
        assertEquals(503, trace.get(0).get("dernierCode").asInt());
        assertEquals(1, trace.get(0).get("tentatives").asInt());
        assertTrue(Instant.parse(trace.get(0).get("prochaineTentative").asText()).isAfter(Instant.now().plusSeconds(30)), "reprise dans une minute");
        assertFalse(trace.get(0).hasNonNull("livreLe"));
        int recues = RECUES.size();
        livraison.executer();
        assertEquals(recues, RECUES.size(), "pas avant l'échéance de reprise");
    }

    @Test
    void les_destinations_internes_inconnues_ou_d_une_autre_application_sont_refusees() throws Exception {
        for (Object corps : List.of(Map.of("url", "https://10.0.0.5/h"), Map.of("url", "http://rh.exemple.ca/h"), Map.of("url", "https://169.254.169.254/latest"),
                Map.of("url", url(), "evenements", List.of("evenement.inconnu")))) {
            MockHttpServletResponse r = appel(HttpMethod.POST, "/api/v1/webhooks", corps);
            assertEquals(422, r.getStatus(), corps + " : " + r.getContentAsString());
            assertTrue(corps(r).get("type").asText().endsWith("destination-webhook-invalide"));
        }
        String destination = corps(appel(HttpMethod.POST, "/api/v1/webhooks", Map.of("url", url()))).get("id").asText();
        app = applications.enregistrer(PORTEES.toArray(String[]::new));
        assertEquals(404, appel(HttpMethod.GET, "/api/v1/webhooks/" + destination + "/deliveries", null).getStatus(), "autre application : introuvable");
        assertEquals(403, mvc.perform(MockMvcRequestBuilders.get("/api/v1/webhooks").with(jwt().jwt(j -> j.subject(app.clientId())
                .claim("org", app.organisation().toString()).claim("client_id", app.clientId())))).andReturn().getResponse().getStatus(), "portée exigée");
    }
}
