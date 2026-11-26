package ca.vinaitsolution.vinasign.acces;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

import ca.vinaitsolution.vinasign.acces.application.ReinitialiserBacASable;
import ca.vinaitsolution.vinasign.acces.domain.BacASableRefuse;
import ca.vinaitsolution.vinasign.commun.domain.ContexteAcces;
import ca.vinaitsolution.vinasign.support.ApplicationsDeTest;
import ca.vinaitsolution.vinasign.support.PostgresDeTest;
import ca.vinaitsolution.vinasign.support.ReferentielsSql;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
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
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/** VS-095 — un intégrateur repart d'un bac à sable vide ; rien n'est effacé, et la production n'est jamais concernée. */
@SpringBootTest
@AutoConfigureMockMvc
@Import({PostgresDeTest.class, ApplicationsDeTest.class, ReferentielsSql.class})
@TestPropertySource(properties = "vinasign.environnement=bac-a-sable")
class BacASableApiTest {

    private static final List<String> PORTEES = List.of("enveloppes-creer", "enveloppes-lire");

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired ApplicationsDeTest applications;
    @Autowired @LiquibaseDataSource DataSource proprietaire;

    private JwtRequestPostProcessor jeton(String client, UUID organisation) {
        return jwt().jwt(j -> j.subject(client).claim("org", organisation.toString()).claim("client_id", client))
                .authorities(PORTEES.stream().<GrantedAuthority>map(p -> new SimpleGrantedAuthority("SCOPE_" + p)).toList());
    }

    @Test
    void l_application_repart_d_une_organisation_vide_au_meme_plan_sans_rien_effacer() throws Exception {
        var app = applications.enregistrer(PORTEES.toArray(String[]::new));
        JdbcTemplate base = new JdbcTemplate(proprietaire);
        base.update("UPDATE application_cliente SET environnement = 'bac-a-sable' WHERE client_id = ?", app.clientId());
        String id = json.readTree(mvc.perform(MockMvcRequestBuilders.post("/api/v1/envelopes").with(jeton(app.clientId(), app.organisation()))
                .header("Idempotency-Key", UUID.randomUUID().toString()).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("externalReference", "BAS-" + UUID.randomUUID(), "mode", "empreinte",
                        "expiresAt", Instant.now().plusSeconds(86400).toString())))).andReturn().getResponse().getContentAsString()).get("id").asText();

        var r = mvc.perform(MockMvcRequestBuilders.post("/api/v1/sandbox/reset").with(jeton(app.clientId(), app.organisation()))).andReturn().getResponse();
        assertEquals(200, r.getStatus(), r.getContentAsString());
        UUID nouvelle = UUID.fromString(json.readTree(r.getContentAsString()).get("organisation").asText());
        assertNotEquals(app.organisation(), nouvelle);
        assertEquals(nouvelle.toString(), base.queryForObject("SELECT tenant_id::text FROM application_cliente WHERE client_id = ?", String.class, app.clientId()));
        assertEquals(base.queryForObject("SELECT plan FROM organisation WHERE id = ?", String.class, app.organisation()),
                base.queryForObject("SELECT plan FROM organisation WHERE id = ?", String.class, nouvelle), "même plan");
        assertEquals(404, mvc.perform(MockMvcRequestBuilders.get("/api/v1/envelopes/" + id).with(jeton(app.clientId(), nouvelle))).andReturn().getResponse()
                .getStatus(), "les anciennes données ne sont plus visibles");
        assertEquals(1, base.queryForObject("SELECT count(*) FROM enveloppe WHERE id = ?::uuid", Integer.class, id), "rien n'est effacé");
    }

    @Test
    void une_application_de_production_ne_se_reinitialise_jamais() throws Exception {
        var app = applications.enregistrer(PORTEES.toArray(String[]::new));
        new JdbcTemplate(proprietaire).update("UPDATE application_cliente SET environnement = 'production' WHERE client_id = ?", app.clientId());
        var r = mvc.perform(MockMvcRequestBuilders.post("/api/v1/sandbox/reset").with(jeton(app.clientId(), app.organisation()))).andReturn().getResponse();
        assertEquals(403, r.getStatus());
        assertTrue(json.readTree(r.getContentAsString()).get("type").asText().endsWith("bac-a-sable-requis"));
    }

    @Test
    void hors_d_une_plateforme_de_bac_a_sable_ou_sans_application_rien_ne_se_reinitialise() {
        assertThrows(BacASableRefuse.class, () -> new ReinitialiserBacASable(null, null, null, Clock.systemUTC(), false).executer());
        assertThrows(BacASableRefuse.class, () -> new ReinitialiserBacASable(null, ContexteAcces::anonyme, null, Clock.systemUTC(), true).executer());
    }
}
