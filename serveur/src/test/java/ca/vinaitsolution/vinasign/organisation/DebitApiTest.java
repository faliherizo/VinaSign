package ca.vinaitsolution.vinasign.organisation;

import static ca.vinaitsolution.vinasign.support.ApplicationsDeTest.assertion;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

import ca.vinaitsolution.vinasign.acces.infrastructure.securite.JetonsProprietes;
import ca.vinaitsolution.vinasign.commun.domain.Libelles;
import ca.vinaitsolution.vinasign.referentiel.domain.CodeReferentiel;
import ca.vinaitsolution.vinasign.referentiel.domain.ValeurReferentiel;
import ca.vinaitsolution.vinasign.support.ApplicationsDeTest;
import ca.vinaitsolution.vinasign.support.ApplicationsDeTest.Application;
import ca.vinaitsolution.vinasign.support.PostgresDeTest;
import ca.vinaitsolution.vinasign.support.ReferentielsSql;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Instant;
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
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/** VS-015 — limitation du débit par plan, sur la vraie pile et la vraie base. */
@SpringBootTest
@AutoConfigureMockMvc
@Import({PostgresDeTest.class, ApplicationsDeTest.class, ReferentielsSql.class})
@TestPropertySource(properties = "vinasign.debit.anonyme-par-minute=3")
class DebitApiTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired ApplicationsDeTest applications;
    @Autowired ReferentielsSql referentiels;
    @Autowired JetonsProprietes proprietes;
    @Autowired MeterRegistry metriques;
    @Autowired @LiquibaseDataSource DataSource proprietaire;

    private static RequestPostProcessor depuis(String adresse) {
        return r -> {
            r.setRemoteAddr(adresse);
            return r;
        };
    }

    private static String adresseUnique() {
        return "10.1." + (int) (Math.random() * 250) + "." + (int) (Math.random() * 250);
    }

    private MockHttpServletResponse demanderJeton(Application a, String adresse) throws Exception {
        Instant t = Instant.now();
        return mvc.perform(MockMvcRequestBuilders.post("/oauth2/token").with(depuis(adresse)).contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("grant_type", "client_credentials").param("client_id", a.clientId())
                .param("client_assertion_type", "urn:ietf:params:oauth:client-assertion-type:jwt-bearer")
                .param("client_assertion", assertion(a.cle(), a.clientId(), a.clientId(), proprietes.emetteur(), UUID.randomUUID().toString(), t,
                        t.plusSeconds(60)))).andReturn().getResponse();
    }

    @Test
    void le_point_de_jeton_est_limite_par_adresse() throws Exception {
        Application a = applications.enregistrer("enveloppes-lire");
        String adresse = adresseUnique();
        for (int i = 0; i < 3; i++) assertEquals(200, demanderJeton(a, adresse).getStatus());
        MockHttpServletResponse limite = demanderJeton(a, adresse);
        assertEquals(429, limite.getStatus());
        assertTrue(Integer.parseInt(limite.getHeader("Retry-After")) >= 1);
        assertEquals(200, demanderJeton(a, adresseUnique()).getStatus(), "une autre adresse a son propre seau");
    }

    @Test
    void une_application_est_limitee_par_le_plan_de_son_organisation_et_comptee() throws Exception {
        Application a = applications.enregistrer("enveloppes-lire");
        String plan = "plan-essai-" + UUID.randomUUID();
        referentiels.ajouterValeurOrganisation("plan", a.organisation(), new ValeurReferentiel(new CodeReferentiel(plan),
                Libelles.de(Map.of("fr", "Plan d'essai")), 9, Map.of("requetesParMinute", "2"), true));
        new JdbcTemplate(proprietaire).update("UPDATE organisation SET plan = ? WHERE id = ?", plan, a.organisation());
        String jeton = json.readTree(demanderJeton(a, adresseUnique()).getContentAsString()).get("access_token").asText();

        for (int i = 0; i < 2; i++) {
            assertEquals(200, mvc.perform(MockMvcRequestBuilders.get("/api/v1/jeton").header("Authorization", "Bearer " + jeton))
                    .andReturn().getResponse().getStatus());
        }
        MockHttpServletResponse limite = mvc.perform(MockMvcRequestBuilders.get("/api/v1/jeton").header("Authorization", "Bearer " + jeton))
                .andReturn().getResponse();
        assertEquals(429, limite.getStatus());
        assertEquals("30", limite.getHeader("Retry-After"), "2 par minute : un jeton toutes les 30 s");
        assertTrue(json.readTree(limite.getContentAsString()).get("type").asText().endsWith("/trop-de-requetes"));
        assertEquals(2.0, metriques.counter("vinasign.api.requetes", "application", a.clientId(), "issue", "acceptee").count());
        assertEquals(1.0, metriques.counter("vinasign.api.requetes", "application", a.clientId(), "issue", "limitee").count());
    }

    @Test
    void la_plateforme_change_le_plan_d_une_organisation_parmi_ceux_du_referentiel() throws Exception {
        JsonNode plans = json.readTree(mvc.perform(MockMvcRequestBuilders.get("/api/v1/referentiels/plan").with(depuis(adresseUnique())))
                .andReturn().getResponse().getContentAsString()).get("valeurs");
        String dernier = plans.get(plans.size() - 1).get("code").asText();
        var admin = jwt().jwt(j -> j.subject("admin-plateforme")).authorities(new SimpleGrantedAuthority("ROLE_administrateur-plateforme"));
        MockHttpServletResponse creee = mvc.perform(MockMvcRequestBuilders.post("/api/v1/organisations").with(admin).with(depuis(adresseUnique()))
                .contentType(MediaType.APPLICATION_JSON).content("{\"nom\":\"Planifiée\"}")).andReturn().getResponse();
        JsonNode org = json.readTree(creee.getContentAsString());
        assertEquals(plans.get(0).get("code").asText(), org.get("plan").asText(), "le plan par défaut du référentiel");
        MockHttpServletResponse change = mvc.perform(MockMvcRequestBuilders.put("/api/v1/organisations/" + org.get("id").asText() + "/plan")
                .with(admin).with(depuis(adresseUnique())).contentType(MediaType.APPLICATION_JSON).content("{\"plan\":\"" + dernier + "\"}"))
                .andReturn().getResponse();
        assertEquals(200, change.getStatus(), change.getContentAsString());
        assertEquals(dernier, json.readTree(change.getContentAsString()).get("plan").asText());
        assertEquals(422, mvc.perform(MockMvcRequestBuilders.put("/api/v1/organisations/" + org.get("id").asText() + "/plan")
                .with(admin).with(depuis(adresseUnique())).contentType(MediaType.APPLICATION_JSON).content("{\"plan\":\"illimite\"}"))
                .andReturn().getResponse().getStatus());
    }
}
