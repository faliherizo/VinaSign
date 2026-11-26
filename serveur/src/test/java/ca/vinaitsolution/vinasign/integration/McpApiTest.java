package ca.vinaitsolution.vinasign.integration;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

import ca.vinaitsolution.vinasign.support.ApplicationsDeTest;
import ca.vinaitsolution.vinasign.support.ApplicationsDeTest.Application;
import ca.vinaitsolution.vinasign.support.PostgresDeTest;
import ca.vinaitsolution.vinasign.support.ReferentielsSql;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/** VS-094 — un assistant d'IA consulte, par MCP et en lecture seule, les enveloppes de l'application qui l'autorise. */
@SpringBootTest
@AutoConfigureMockMvc
@Import({PostgresDeTest.class, ApplicationsDeTest.class, ReferentielsSql.class})
class McpApiTest {

    private static final List<String> PORTEES = List.of("enveloppes-creer", "enveloppes-lire", "signataires-gerer");

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired ApplicationsDeTest applications;

    private Application app;

    @BeforeEach
    void preparer() {
        app = applications.enregistrer(PORTEES.toArray(String[]::new));
    }

    private JwtRequestPostProcessor jeton(Application a, List<String> portees) {
        return jwt().jwt(j -> j.subject(a.clientId()).claim("org", a.organisation().toString()).claim("client_id", a.clientId()))
                .authorities(portees.stream().<GrantedAuthority>map(p -> new SimpleGrantedAuthority("SCOPE_" + p)).toList());
    }

    private MockHttpServletResponse mcp(Application a, String corps) throws Exception {
        return mvc.perform(MockMvcRequestBuilders.post("/api/v1/mcp").with(jeton(a, PORTEES)).contentType(MediaType.APPLICATION_JSON).content(corps))
                .andReturn().getResponse();
    }

    private JsonNode appel(Application a, String methode, Object params) throws Exception {
        MockHttpServletResponse r = mcp(a, json.writeValueAsString(Map.of("jsonrpc", "2.0", "id", 7, "method", methode, "params", params)));
        assertEquals(200, r.getStatus(), r.getContentAsString());
        return json.readTree(r.getContentAsString(StandardCharsets.UTF_8));
    }

    private String enveloppe() throws Exception {
        String id = json.readTree(mvc.perform(MockMvcRequestBuilders.post("/api/v1/envelopes").with(jeton(app, PORTEES))
                .header("Idempotency-Key", UUID.randomUUID().toString()).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("externalReference", "MCP-" + UUID.randomUUID(), "mode", "empreinte",
                        "expiresAt", Instant.now().plusSeconds(86400).toString())))).andReturn().getResponse().getContentAsString()).get("id").asText();
        mvc.perform(MockMvcRequestBuilders.put("/api/v1/envelopes/" + id + "/signers").with(jeton(app, PORTEES)).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(List.of(Map.of("nom", "Alice Tremblay", "courriel", "alice-mcp@exemple.ca", "langue", "fr",
                        "type", "invite", "ordre", 1)))));
        return id;
    }

    @Test
    void l_assistant_decouvre_des_outils_en_lecture_seule_et_lit_une_enveloppe_sans_renseignement_personnel() throws Exception {
        JsonNode init = appel(app, "initialize", Map.of("protocolVersion", "2025-06-18"));
        assertEquals("2025-06-18", init.at("/result/protocolVersion").asText());
        assertEquals(ca.vinaitsolution.vinasign.integration.infrastructure.mcp.ServeurMcp.VERSIONS.get(0),
                appel(app, "initialize", Map.of("protocolVersion", "1999-01-01")).at("/result/protocolVersion").asText(), "version inconnue : la plus récente");
        assertEquals(0, appel(app, "ping", Map.of()).at("/result").size());
        JsonNode outils = appel(app, "tools/list", Map.of()).at("/result/tools");
        assertEquals(List.of("lire_enveloppe", "lire_referentiel"), List.of(outils.get(0).get("name").asText(), outils.get(1).get("name").asText()));
        outils.forEach(o -> assertTrue(o.at("/annotations/readOnlyHint").asBoolean()));

        String id = enveloppe();
        JsonNode lu = appel(app, "tools/call", Map.of("name", "lire_enveloppe", "arguments", Map.of("enveloppeId", id)));
        assertFalse(lu.at("/result/isError").asBoolean(), lu.toString());
        assertEquals("brouillon", lu.at("/result/structuredContent/statut").asText());
        assertEquals(1, lu.at("/result/structuredContent/signataires/0/ordre").asInt());
        assertFalse(lu.toString().contains("Alice") || lu.toString().contains("alice-mcp@"), "aucun renseignement personnel");

        JsonNode ref = appel(app, "tools/call", Map.of("name", "lire_referentiel", "arguments", Map.of("code", "statut-enveloppe")));
        assertTrue(ref.at("/result/structuredContent/valeurs").size() >= 5);
    }

    @Test
    void les_refus_sont_des_resultats_que_l_assistant_peut_corriger_ou_des_erreurs_de_protocole() throws Exception {
        String id = enveloppe();
        Application autre = applications.enregistrer(PORTEES.toArray(String[]::new));
        JsonNode etrangere = appel(autre, "tools/call", Map.of("name", "lire_enveloppe", "arguments", Map.of("enveloppeId", id)));
        assertTrue(etrangere.at("/result/isError").asBoolean(), "enveloppe d'une autre application : introuvable");
        assertTrue(appel(app, "tools/call", Map.of("name", "lire_enveloppe", "arguments", Map.of("enveloppeId", "pas-un-uuid")))
                .at("/result/isError").asBoolean());
        assertTrue(appel(app, "tools/call", Map.of("name", "lire_referentiel", "arguments", Map.of("code", "inconnu-xyz"))).at("/result/isError").asBoolean());
        assertTrue(appel(app, "tools/call", Map.of("name", "lire_referentiel")).at("/result/isError").asBoolean(), "sans argument");
        assertEquals(-32602, appel(app, "tools/call", Map.of("name", "ecrire_tout")).at("/error/code").asInt());
        assertEquals(-32602, appel(app, "tools/call", Map.of("name", "lire_enveloppe", "arguments", List.of(1))).at("/error/code").asInt());
        assertEquals(-32601, appel(app, "supprimer", Map.of()).at("/error/code").asInt());
        assertEquals(202, mcp(app, "{\"jsonrpc\":\"2.0\",\"method\":\"notifications/initialized\"}").getStatus(), "notification : aucune réponse");
        assertEquals(-32600, json.readTree(mcp(app, "{\"jsonrpc\":\"1.0\",\"id\":1}").getContentAsString()).at("/error/code").asInt());
        assertEquals(400, mcp(app, "[{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"ping\"}]").getStatus(), "lots refusés");
        assertEquals(400, mcp(app, "{pas du json").getStatus());
        assertEquals(400, mvc.perform(MockMvcRequestBuilders.post("/api/v1/mcp").with(jeton(app, PORTEES)).header("MCP-Protocol-Version", "1999-01-01")
                .contentType(MediaType.APPLICATION_JSON).content("{}")).andReturn().getResponse().getStatus());
        assertEquals(200, mvc.perform(MockMvcRequestBuilders.post("/api/v1/mcp").with(jeton(app, PORTEES)).header("MCP-Protocol-Version", "2025-06-18")
                .contentType(MediaType.APPLICATION_JSON).content("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"ping\"}")).andReturn().getResponse().getStatus());
        assertEquals(405, mvc.perform(MockMvcRequestBuilders.get("/api/v1/mcp").with(jeton(app, PORTEES))).andReturn().getResponse().getStatus());
        assertEquals(403, mvc.perform(MockMvcRequestBuilders.post("/api/v1/mcp").with(jeton(app, List.of("enveloppes-creer")))
                .contentType(MediaType.APPLICATION_JSON).content("{}")).andReturn().getResponse().getStatus(), "portée enveloppes-lire exigée");
    }
}
