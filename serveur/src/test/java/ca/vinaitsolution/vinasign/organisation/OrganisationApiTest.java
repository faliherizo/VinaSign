package ca.vinaitsolution.vinasign.organisation;

import static ca.vinaitsolution.vinasign.support.ApplicationsDeTest.assertion;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

import ca.vinaitsolution.vinasign.acces.infrastructure.securite.JetonsProprietes;
import ca.vinaitsolution.vinasign.support.ApplicationsDeTest;
import ca.vinaitsolution.vinasign.support.ApplicationsDeTest.Application;
import ca.vinaitsolution.vinasign.support.PostgresDeTest;
import ca.vinaitsolution.vinasign.support.ReferentielsSql;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.liquibase.LiquibaseDataSource;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/** VS-010 — administration des organisations, sur la vraie pile et la vraie base. */
@SpringBootTest
@AutoConfigureMockMvc
@Import({PostgresDeTest.class, ApplicationsDeTest.class, ReferentielsSql.class})
class OrganisationApiTest {

    private static final String ADMIN = "admin-plateforme-" + UUID.randomUUID();

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired ApplicationsDeTest applications;
    @Autowired JetonsProprietes proprietes;
    @Autowired @LiquibaseDataSource DataSource proprietaire;

    private static JwtRequestPostProcessor administrateur() {
        return jwt().jwt(j -> j.subject(ADMIN)).authorities(new SimpleGrantedAuthority("ROLE_administrateur-plateforme"));
    }

    private MockHttpServletResponse post(String url, String corps, JwtRequestPostProcessor qui) throws Exception {
        var r = MockMvcRequestBuilders.post(url).with(qui).contentType(MediaType.APPLICATION_JSON);
        if (corps != null) r = r.content(corps);
        return mvc.perform(r).andReturn().getResponse();
    }

    private JsonNode corps(MockHttpServletResponse r) throws Exception {
        return json.readTree(r.getContentAsString());
    }

    private UUID creer(String nom) throws Exception {
        MockHttpServletResponse r = post("/api/v1/organisations", "{\"nom\":\"" + nom + "\"}", administrateur());
        assertEquals(201, r.getStatus(), r.getContentAsString());
        UUID id = UUID.fromString(corps(r).get("id").asText());
        assertEquals("/api/v1/organisations/" + id, r.getHeader("Location"));
        return id;
    }

    private MockHttpServletResponse demanderJeton(Application a) throws Exception {
        Instant t = Instant.now();
        return mvc.perform(MockMvcRequestBuilders.post("/oauth2/token").contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("grant_type", "client_credentials").param("client_id", a.clientId())
                .param("client_assertion_type", "urn:ietf:params:oauth:client-assertion-type:jwt-bearer")
                .param("client_assertion", assertion(a.cle(), a.clientId(), a.clientId(), proprietes.emetteur(),
                        UUID.randomUUID().toString(), t, t.plusSeconds(60)))).andReturn().getResponse();
    }

    private MockHttpServletResponse api(String jeton) throws Exception {
        return mvc.perform(MockMvcRequestBuilders.get("/api/v1/jeton").header("Authorization", "Bearer " + jeton))
                .andReturn().getResponse();
    }

    @Test
    void une_organisation_suspendue_perd_tout_acces_puis_le_retrouve_a_la_reactivation() throws Exception {
        UUID organisation = creer("Clinique Lévis");
        Application app = applications.enregistrer(organisation, "enveloppes-lire");
        MockHttpServletResponse emis = demanderJeton(app);
        assertEquals(200, emis.getStatus(), emis.getContentAsString());
        String jeton = corps(emis).get("access_token").asText();
        assertEquals(200, api(jeton).getStatus());

        MockHttpServletResponse suspension = post("/api/v1/organisations/" + organisation + "/suspension", null, administrateur());
        assertEquals(200, suspension.getStatus());
        assertEquals("suspendue", corps(suspension).get("statut").asText());

        MockHttpServletResponse refus = api(jeton);
        assertEquals(403, refus.getStatus(), "un jeton encore valide ne sert plus");
        assertEquals(MediaType.APPLICATION_PROBLEM_JSON_VALUE, refus.getContentType());
        assertTrue(corps(refus).get("type").asText().endsWith("/organisation-suspendue"));
        assertEquals(401, demanderJeton(app).getStatus(), "aucun nouveau jeton pour une organisation suspendue");

        assertEquals(200, post("/api/v1/organisations/" + organisation + "/reactivation", null, administrateur()).getStatus());
        assertEquals(200, api(jeton).getStatus());
        assertEquals(200, demanderJeton(app).getStatus());
    }

    @Test
    void chaque_changement_est_consigne_au_journal_d_audit_avec_son_acteur() throws Exception {
        UUID organisation = creer("Organisation auditée");
        post("/api/v1/organisations/" + organisation + "/suspension", null, administrateur());
        post("/api/v1/organisations/" + organisation + "/reactivation", null, administrateur());

        List<Map<String, Object>> lignes = new JdbcTemplate(proprietaire).queryForList(
                "SELECT acteur, action, cible FROM journal_audit WHERE tenant_id = ? ORDER BY survenu_le, action", organisation);
        assertEquals(3, lignes.size());
        assertEquals(List.of("organisation-creee", "organisation-reactivee", "organisation-suspendue"),
                lignes.stream().map(l -> (String) l.get("action")).sorted().toList());
        lignes.forEach(l -> assertEquals(ADMIN, l.get("acteur")));
        lignes.forEach(l -> assertEquals(organisation.toString(), l.get("cible")));
    }

    @Test
    void la_consultation_rend_l_organisation_et_ses_dates() throws Exception {
        UUID organisation = creer("Consultée");
        MockHttpServletResponse r = mvc.perform(MockMvcRequestBuilders.get("/api/v1/organisations/" + organisation)
                .with(administrateur())).andReturn().getResponse();
        assertEquals(200, r.getStatus());
        assertEquals("Consultée", corps(r).get("nom").asText());
        assertEquals("active", corps(r).get("statut").asText());
        assertNotNull(corps(r).get("creeLe"));
    }

    @Test
    void seul_l_administrateur_de_la_plateforme_administre_les_organisations() throws Exception {
        JwtRequestPostProcessor application = jwt().authorities(new SimpleGrantedAuthority("SCOPE_enveloppes-lire"));
        assertEquals(403, post("/api/v1/organisations", "{\"nom\":\"Intruse\"}", application).getStatus());
        UUID organisation = creer("Protégée");
        assertEquals(403, post("/api/v1/organisations/" + organisation + "/suspension", null, application).getStatus());
        assertEquals(401, mvc.perform(MockMvcRequestBuilders.post("/api/v1/organisations").contentType(MediaType.APPLICATION_JSON)
                .content("{\"nom\":\"Anonyme\"}")).andReturn().getResponse().getStatus());
    }

    @Test
    void les_erreurs_sont_typees() throws Exception {
        MockHttpServletResponse inconnue = post("/api/v1/organisations/" + UUID.randomUUID() + "/suspension", null, administrateur());
        assertEquals(404, inconnue.getStatus());
        assertTrue(corps(inconnue).get("type").asText().endsWith("/organisation-introuvable"));

        UUID organisation = creer("Déjà active");
        MockHttpServletResponse conflit = post("/api/v1/organisations/" + organisation + "/reactivation", null, administrateur());
        assertEquals(409, conflit.getStatus());
        assertTrue(corps(conflit).get("type").asText().endsWith("/transition-organisation-interdite"));

        assertEquals(400, post("/api/v1/organisations", "{\"nom\":\"\"}", administrateur()).getStatus());
        assertEquals(400, post("/api/v1/organisations", "{\"nom\":\"" + "x".repeat(201) + "\"}", administrateur()).getStatus());
    }
}
