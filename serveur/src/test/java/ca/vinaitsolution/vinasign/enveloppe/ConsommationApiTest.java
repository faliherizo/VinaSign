package ca.vinaitsolution.vinasign.enveloppe;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

import ca.vinaitsolution.vinasign.support.ApplicationsDeTest;
import ca.vinaitsolution.vinasign.support.ApplicationsDeTest.Application;
import ca.vinaitsolution.vinasign.support.PostgresDeTest;
import ca.vinaitsolution.vinasign.support.ReferentielsSql;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
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

/** VS-120 — consommation de l'organisation : enveloppes comptées une fois au premier envoi ; relevé et export CSV. */
@SpringBootTest
@AutoConfigureMockMvc
@Import({PostgresDeTest.class, ApplicationsDeTest.class, ReferentielsSql.class})
class ConsommationApiTest {

    private static final List<String> PORTEES = List.of("enveloppes-creer", "enveloppes-lire", "enveloppes-envoyer", "signataires-gerer");

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired ApplicationsDeTest applications;

    private JwtRequestPostProcessor jeton(Application a) {
        return jwt().jwt(j -> j.subject(a.clientId()).claim("org", a.organisation().toString()).claim("client_id", a.clientId()))
                .authorities(PORTEES.stream().<GrantedAuthority>map(p -> new SimpleGrantedAuthority("SCOPE_" + p)).toList());
    }

    private MockHttpServletResponse appel(Application a, MockHttpServletRequestBuilder r, Object corps) throws Exception {
        r = r.with(jeton(a)).header("Idempotency-Key", UUID.randomUUID().toString());
        if (corps != null) r = r.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(corps));
        return mvc.perform(r).andReturn().getResponse();
    }

    private String enveloppe(Application a, boolean envoyer, boolean annuler) throws Exception {
        String id = json.readTree(appel(a, MockMvcRequestBuilders.post("/api/v1/envelopes"), Map.of("externalReference", "U-" + UUID.randomUUID(),
                "mode", "empreinte", "expiresAt", Instant.now().plusSeconds(86400).toString())).getContentAsString()).get("id").asText();
        appel(a, MockMvcRequestBuilders.put("/api/v1/envelopes/" + id + "/signers"), List.of(Map.of("nom", "A", "courriel", "a@exemple.ca", "langue", "fr",
                "type", "invite", "ordre", 1)));
        if (envoyer) {
            assertEquals(200, appel(a, MockMvcRequestBuilders.post("/api/v1/envelopes/" + id + "/send"), null).getStatus());
        }
        if (annuler) {
            appel(a, MockMvcRequestBuilders.post("/api/v1/envelopes/" + id + "/cancel"), null);
        }
        return id;
    }

    @Test
    void la_consommation_de_l_organisation_se_releve_et_s_exporte() throws Exception {
        Application app = applications.enregistrer(PORTEES.toArray(String[]::new));
        Application voisine = applications.enregistrer(app.organisation(), PORTEES.toArray(String[]::new));
        enveloppe(app, true, false);
        enveloppe(voisine, true, true);
        enveloppe(app, false, true);
        enveloppe(applications.enregistrer(PORTEES.toArray(String[]::new)), true, false);
        String aujourdhui = LocalDate.now(ZoneId.of("America/Toronto")).toString();
        JsonNode r = json.readTree(appel(app, MockMvcRequestBuilders.get("/api/v1/usage?du=" + aujourdhui + "&au=" + aujourdhui), null).getContentAsString());
        assertEquals(2, r.get("envoyees").asInt(), "les deux applications de l'organisation, pas l'autre organisation");
        assertEquals(1, r.get("annuleesApresEnvoi").asInt());
        assertEquals(1, r.get("annuleesAvantEnvoi").asInt());
        assertEquals(2, r.get("parMois").get(aujourdhui.substring(0, 7)).asInt());
        MockHttpServletResponse csv = appel(app, MockMvcRequestBuilders.get("/api/v1/usage.csv?du=" + aujourdhui + "&au=" + aujourdhui), null);
        assertEquals("mois,enveloppes\r\n" + aujourdhui.substring(0, 7) + ",2\r\n", csv.getContentAsString());
        assertTrue(csv.getHeader("Content-Disposition").contains("attachment"));
        assertEquals(422, appel(app, MockMvcRequestBuilders.get("/api/v1/usage?du=2026-02-01&au=2026-01-01"), null).getStatus());
        assertEquals(422, appel(app, MockMvcRequestBuilders.get("/api/v1/usage?du=2024-01-01&au=2026-01-01"), null).getStatus(), "plus de 400 jours");
    }
}
