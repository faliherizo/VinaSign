package ca.vinaitsolution.vinasign.organisation;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

import ca.vinaitsolution.vinasign.support.ApplicationsDeTest;
import ca.vinaitsolution.vinasign.support.ApplicationsDeTest.Application;
import ca.vinaitsolution.vinasign.support.InvitationsCapturees;
import ca.vinaitsolution.vinasign.support.PostgresDeTest;
import ca.vinaitsolution.vinasign.support.ReferentielsSql;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/**
 * VS-123 — l'organisation définit sa marque (contraste calculé, refusé sous 4,5:1 ; logo PNG ; sous-domaine unique) et le
 * signataire la reçoit à l'ouverture de sa session.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({PostgresDeTest.class, ApplicationsDeTest.class, ReferentielsSql.class, InvitationsCapturees.class})
class MarqueApiTest {

    private static final List<String> PORTEES = List.of("enveloppes-creer", "enveloppes-lire", "enveloppes-envoyer", "signataires-gerer", "marque-gerer");
    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 13, 10, 26, 10};

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired ApplicationsDeTest applications;
    @Autowired InvitationsCapturees invitations;

    private JwtRequestPostProcessor jeton(Application a, List<String> portees) {
        return jwt().jwt(j -> j.subject(a.clientId()).claim("org", a.organisation().toString()).claim("client_id", a.clientId()))
                .authorities(portees.stream().<GrantedAuthority>map(p -> new SimpleGrantedAuthority("SCOPE_" + p)).toList());
    }

    private MockHttpServletResponse appel(Application a, MockHttpServletRequestBuilder r, Object corps) throws Exception {
        r = r.with(jeton(a, PORTEES)).header("Idempotency-Key", UUID.randomUUID().toString());
        if (corps != null) r = r.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(corps));
        return mvc.perform(r).andReturn().getResponse();
    }

    private JsonNode corps(MockHttpServletResponse r) throws Exception {
        return json.readTree(r.getContentAsString(StandardCharsets.UTF_8));
    }

    private static Map<String, Object> marque(String couleur, String logo, String sousDomaine) {
        Map<String, Object> m = new HashMap<>(Map.of("nomAffiche", "Clinique Nord", "couleurPrimaire", couleur));
        if (logo != null) m.put("logo", logo);
        if (sousDomaine != null) m.put("sousDomaine", sousDomaine);
        return m;
    }

    private MockHttpServletResponse definir(Application a, Map<String, Object> m) throws Exception {
        return appel(a, MockMvcRequestBuilders.put("/api/v1/branding"), m);
    }

    private void refusee(MockHttpServletResponse r, int statut, String type) throws Exception {
        assertEquals(statut, r.getStatus(), r.getContentAsString());
        assertTrue(corps(r).get("type").asText().endsWith(type), r.getContentAsString());
    }

    @Test
    void la_marque_se_definit_se_relit_et_reste_propre_a_l_organisation() throws Exception {
        Application app = applications.enregistrer(PORTEES.toArray(String[]::new));
        Application autre = applications.enregistrer(PORTEES.toArray(String[]::new));
        assertEquals(404, appel(app, MockMvcRequestBuilders.get("/api/v1/branding"), null).getStatus(), "aucune marque encore");
        String sd = "nord-" + UUID.randomUUID().toString().substring(0, 8);
        String logo = Base64.getEncoder().encodeToString(PNG);
        assertEquals(200, definir(app, marque("#0B5394", logo, sd)).getStatus());
        assertEquals(200, definir(app, marque("#0b5394", logo, sd)).getStatus(), "redéfinir remplace");
        JsonNode lue = corps(appel(app, MockMvcRequestBuilders.get("/api/v1/branding"), null));
        assertEquals("#0b5394", lue.get("couleurPrimaire").asText());
        assertEquals(logo, lue.get("logo").asText());
        assertEquals(sd, lue.get("sousDomaine").asText());
        assertEquals(404, appel(autre, MockMvcRequestBuilders.get("/api/v1/branding"), null).getStatus(), "isolée par organisation");
        refusee(definir(autre, marque("#000000", null, sd)), 409, "sous-domaine-pris");
        assertEquals(200, definir(autre, marque("#000000", " ", null)).getStatus(), "logo vide : aucun");
        assertFalse(corps(appel(autre, MockMvcRequestBuilders.get("/api/v1/branding"), null)).hasNonNull("logo"));
    }

    @Test
    void une_marque_illisible_ou_un_appel_sans_portee_est_refuse() throws Exception {
        Application app = applications.enregistrer(PORTEES.toArray(String[]::new));
        refusee(definir(app, marque("#777777", null, null)), 422, "marque-invalide");
        refusee(definir(app, marque("#000000", "pas du base64 !", null)), 422, "marque-invalide");
        refusee(definir(app, marque("#000000", Base64.getEncoder().encodeToString(new byte[] {1, 2, 3, 4}), null)), 422, "marque-invalide");
        assertEquals(403, mvc.perform(MockMvcRequestBuilders.get("/api/v1/branding").with(jeton(app, List.of("enveloppes-lire"))))
                .andReturn().getResponse().getStatus());
        JwtRequestPostProcessor administrateur = jwt().jwt(j -> j.subject("admin-" + UUID.randomUUID()).claim("org", app.organisation().toString()))
                .authorities(new SimpleGrantedAuthority("ROLE_administrateur-organisation"));
        assertEquals(404, mvc.perform(MockMvcRequestBuilders.get("/api/v1/branding").with(administrateur)).andReturn().getResponse().getStatus(),
                "l'administrateur de l'organisation y accède au portail");
    }

    @Test
    void le_signataire_recoit_la_marque_a_l_ouverture() throws Exception {
        Application app = applications.enregistrer(PORTEES.toArray(String[]::new));
        assertEquals(200, definir(app, marque("#0b5394", Base64.getEncoder().encodeToString(PNG), null)).getStatus());
        String id = corps(appel(app, MockMvcRequestBuilders.post("/api/v1/envelopes"), Map.of("externalReference", "M-" + UUID.randomUUID(),
                "mode", "empreinte", "expiresAt", Instant.now().plusSeconds(86400).toString()))).get("id").asText();
        appel(app, MockMvcRequestBuilders.put("/api/v1/envelopes/" + id + "/signers"), List.of(Map.of("nom", "A", "courriel", "a@exemple.ca",
                "langue", "fr", "type", "invite", "ordre", 1)));
        assertEquals(200, appel(app, MockMvcRequestBuilders.post("/api/v1/envelopes/" + id + "/send"), null).getStatus());
        JsonNode session = corps(mvc.perform(MockMvcRequestBuilders.post("/api/v1/signataire/ouverture").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("jeton", invitations.pour(id).get(0).get("jeton"))))).andReturn().getResponse());
        assertEquals("Clinique Nord", session.at("/marque/nomAffiche").asText());
        assertEquals("#0b5394", session.at("/marque/couleurPrimaire").asText());
        assertEquals("data:image/png;base64," + Base64.getEncoder().encodeToString(PNG), session.at("/marque/logo").asText());
    }
}
