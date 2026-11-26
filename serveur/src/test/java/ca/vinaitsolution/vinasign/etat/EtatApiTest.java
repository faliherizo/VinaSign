package ca.vinaitsolution.vinasign.etat;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

import ca.vinaitsolution.vinasign.support.PostgresDeTest;
import ca.vinaitsolution.vinasign.support.ReferentielsSql;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
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

/**
 * VS-124 — l'administrateur de la plateforme publie un incident ; la page d'état publique en déduit l'impact de chaque
 * composant et l'impact global, puis l'historique ; les codes viennent des référentiels, découverts par l'API.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({PostgresDeTest.class, ReferentielsSql.class})
class EtatApiTest {

    private static final JwtRequestPostProcessor PLATEFORME = jwt().jwt(j -> j.subject("exploitation"))
            .authorities(new SimpleGrantedAuthority("ROLE_administrateur-plateforme"));

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired @LiquibaseDataSource DataSource proprietaire;

    @BeforeEach
    void vider() {
        new JdbcTemplate(proprietaire).update("DELETE FROM incident_mise_a_jour");
        new JdbcTemplate(proprietaire).update("DELETE FROM incident");
    }

    private JsonNode corps(MockHttpServletResponse r) throws Exception {
        return json.readTree(r.getContentAsString(StandardCharsets.UTF_8));
    }

    private List<String> codes(String referentiel) throws Exception {
        List<String> codes = new ArrayList<>();
        corps(mvc.perform(MockMvcRequestBuilders.get("/api/v1/referentiels/" + referentiel)).andReturn().getResponse()).get("valeurs")
                .forEach(v -> codes.add(v.get("code").asText()));
        return codes;
    }

    private MockHttpServletResponse poster(String url, Object corps, JwtRequestPostProcessor qui) throws Exception {
        return mvc.perform(MockMvcRequestBuilders.post(url).with(qui).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(corps)))
                .andReturn().getResponse();
    }

    private JsonNode etat() throws Exception {
        MockHttpServletResponse r = mvc.perform(MockMvcRequestBuilders.get("/api/v1/availability")).andReturn().getResponse();
        assertEquals(200, r.getStatus(), "public, sans jeton");
        assertTrue(r.getHeader("Cache-Control").contains("max-age=60"));
        return corps(r);
    }

    @Test
    void un_incident_publie_change_l_etat_puis_entre_dans_l_historique() throws Exception {
        List<String> impacts = codes("impact-incident");
        List<String> composants = codes("composant-service");
        List<String> phases = codes("phase-incident");
        String normal = impacts.get(0);
        String grave = impacts.get(impacts.size() - 1);
        String touche = composants.get(0);
        JsonNode avant = etat();
        assertEquals(normal, avant.get("impact").asText());
        assertEquals(composants.size(), avant.get("composants").size());
        assertTrue(codes("objectif-service").size() >= 1, "objectifs de service publiés par référentiel");

        MockHttpServletResponse ouvert = poster("/api/v1/platform/incidents", Map.of("titre", Map.of("fr", "Lenteurs", "en", "Slowness"),
                "impact", grave, "composants", List.of(touche), "communication", Map.of("phase", phases.get(0), "message", Map.of("fr", "Nous enquêtons."))),
                PLATEFORME);
        assertEquals(201, ouvert.getStatus(), ouvert.getContentAsString());
        String id = corps(ouvert).get("id").asText();
        JsonNode pendant = etat();
        assertEquals(grave, pendant.get("impact").asText());
        assertEquals(grave, pendant.at("/composants/" + touche).asText());
        assertEquals(normal, pendant.at("/composants/" + composants.get(composants.size() - 1)).asText(), "autre composant épargné");
        assertEquals("Slowness", pendant.at("/incidents/0/titre/en").asText());

        String cloture = phases.get(phases.size() - 1);
        assertEquals(200, poster("/api/v1/platform/incidents/" + id + "/updates", Map.of("phase", phases.get(1), "message", Map.of("fr", "Cause trouvée.")),
                PLATEFORME).getStatus());
        MockHttpServletResponse resolu = poster("/api/v1/platform/incidents/" + id + "/updates", Map.of("phase", cloture,
                "message", Map.of("fr", "Résolu.")), PLATEFORME);
        assertFalse(corps(resolu).get("fin").isNull(), resolu.getContentAsString());
        JsonNode apres = etat();
        assertEquals(normal, apres.get("impact").asText());
        assertEquals(3, apres.at("/incidents/0/misesAJour").size(), "historique conservé");
        assertEquals(409, poster("/api/v1/platform/incidents/" + id + "/updates", Map.of("phase", phases.get(0), "message", Map.of("fr", "x")),
                PLATEFORME).getStatus(), "incident résolu : figé");
    }

    @Test
    void seule_la_plateforme_publie_et_seulement_des_codes_connus() throws Exception {
        Map<String, Object> incident = Map.of("titre", Map.of("fr", "x"), "impact", codes("impact-incident").get(1),
                "composants", List.of(codes("composant-service").get(0)), "communication", Map.of("phase", codes("phase-incident").get(0),
                        "message", Map.of("fr", "x")));
        JwtRequestPostProcessor organisation = jwt().authorities(new SimpleGrantedAuthority("ROLE_administrateur-organisation"));
        assertEquals(403, poster("/api/v1/platform/incidents", incident, organisation).getStatus());
        assertEquals(401, mvc.perform(MockMvcRequestBuilders.post("/api/v1/platform/incidents").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(incident))).andReturn().getResponse().getStatus());
        Map<String, Object> inconnu = new java.util.HashMap<>(incident);
        inconnu.put("composants", List.of("composant-inexistant"));
        assertEquals(422, poster("/api/v1/platform/incidents", inconnu, PLATEFORME).getStatus());
        assertEquals(404, poster("/api/v1/platform/incidents/" + java.util.UUID.randomUUID() + "/updates",
                Map.of("phase", codes("phase-incident").get(0), "message", Map.of("fr", "x")), PLATEFORME).getStatus());
        assertEquals(400, poster("/api/v1/platform/incidents", Map.of("titre", Map.of()), PLATEFORME).getStatus());
    }
}
