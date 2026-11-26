package ca.vinaitsolution.vinasign.organisation;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

import ca.vinaitsolution.vinasign.support.DnsDeTest;
import ca.vinaitsolution.vinasign.support.PostgresDeTest;
import ca.vinaitsolution.vinasign.support.ReferentielsSql;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.sql.DataSource;
import org.junit.jupiter.api.Nested;
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
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/**
 * VS-122 — inscription en libre-service : au bac à sable, le courriel confirmé crée l'organisation et invite son
 * administrateur ; en production, le domaine se prouve par un enregistrement TXT et l'administrateur de la plateforme valide.
 */
class InscriptionApiTest {

    private static final Pattern JETON = Pattern.compile("jeton=([A-Za-z0-9_-]+)");
    private static final JwtRequestPostProcessor PLATEFORME = jwt().jwt(j -> j.subject("exploitation"))
            .authorities(new SimpleGrantedAuthority("ROLE_administrateur-plateforme"));

    abstract static class Base {

        @Autowired MockMvc mvc;
        @Autowired ObjectMapper json;
        @Autowired @LiquibaseDataSource DataSource proprietaire;

        MockHttpServletResponse poster(String url, Object corps) throws Exception {
            return mvc.perform(MockMvcRequestBuilders.post(url).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(corps)))
                    .andReturn().getResponse();
        }

        JsonNode corps(MockHttpServletResponse r) throws Exception {
            return json.readTree(r.getContentAsString(StandardCharsets.UTF_8));
        }

        /** Jeton du dernier courriel de confirmation reçu par ce destinataire. */
        String jetonRecu(String courriel) {
            String corps = new JdbcTemplate(proprietaire).queryForObject(
                    "SELECT corps FROM courriel_sortant WHERE destinataire = ? ORDER BY cree_le DESC LIMIT 1", String.class, courriel);
            Matcher m = JETON.matcher(corps);
            assertTrue(m.find(), corps);
            return m.group(1);
        }

        int courriels(String courriel) {
            return new JdbcTemplate(proprietaire).queryForObject("SELECT count(*) FROM courriel_sortant WHERE destinataire = ?", Integer.class, courriel);
        }
    }

    @Nested
    @SpringBootTest
    @AutoConfigureMockMvc
    @Import({PostgresDeTest.class, ReferentielsSql.class})
    @TestPropertySource(properties = "vinasign.inscription.mode=bac-a-sable")
    class BacASable extends Base {

        @Test
        void le_courriel_confirme_cree_l_organisation_et_invite_son_administrateur() throws Exception {
            String courriel = "ana-" + UUID.randomUUID() + "@exemple.ca";
            Map<String, String> demande = Map.of("organisation", "Clinique Nord", "courriel", courriel, "langue", "fr");
            assertEquals(202, poster("/api/v1/signup", demande).getStatus());
            assertEquals(202, poster("/api/v1/signup", demande).getStatus(), "même réponse pour une demande déjà en attente");
            assertEquals(1, courriels(courriel), "aucun courriel multiplié");
            String jeton = jetonRecu(courriel);
            assertEquals("courriel-a-confirmer", corps(poster("/api/v1/signup/consultation", Map.of("jeton", jeton))).get("etape").asText());

            JsonNode confirmee = corps(poster("/api/v1/signup/confirmation", Map.of("jeton", jeton)));
            assertEquals("acceptee", confirmee.get("etape").asText());
            String org = confirmee.get("id").asText();
            JdbcTemplate base = new JdbcTemplate(proprietaire);
            assertEquals("Clinique Nord", base.queryForObject("SELECT nom FROM organisation WHERE id = ?::uuid", String.class, org));
            assertEquals("{administrateur-organisation}", base.queryForObject(
                    "SELECT roles::text FROM utilisateur_portail WHERE tenant_id = ?::uuid AND courriel = ?", String.class, org, courriel));
            assertEquals(org, base.queryForObject("SELECT tenant_id::text FROM courriel_sortant WHERE destinataire = ? LIMIT 1", String.class, courriel),
                    "le courriel de confirmation appartient à la future organisation");
            assertEquals(422, poster("/api/v1/signup/confirmation", Map.of("jeton", jeton)).getStatus(), "lien déjà utilisé");
            assertEquals(404, poster("/api/v1/signup/consultation", Map.of("jeton", "inconnu")).getStatus());
            assertEquals(400, poster("/api/v1/signup", Map.of("organisation", "x", "courriel", courriel, "langue", "francais")).getStatus());
        }

        @Test
        void un_lien_expire_laisse_refaire_la_demande() throws Exception {
            String courriel = "lent-" + UUID.randomUUID() + "@exemple.ca";
            Map<String, String> demande = Map.of("organisation", "Lente", "courriel", courriel, "langue", "fr");
            poster("/api/v1/signup", demande);
            new JdbcTemplate(proprietaire).update("UPDATE inscription SET jeton_expire_le = now() - interval '1 second' WHERE courriel = ?", courriel);
            assertEquals(202, poster("/api/v1/signup", demande).getStatus());
            assertEquals(2, courriels(courriel), "nouveau lien envoyé");
            assertEquals("refusee", new JdbcTemplate(proprietaire).queryForObject(
                    "SELECT etat FROM inscription WHERE courriel = ? ORDER BY cree_le LIMIT 1", String.class, courriel));
        }
    }

    @Nested
    @SpringBootTest
    @AutoConfigureMockMvc
    @Import({PostgresDeTest.class, ReferentielsSql.class})
    @TestPropertySource(properties = "vinasign.inscription.mode=production")
    class Production extends Base {

        static final DnsDeTest DNS = demarrer();

        static DnsDeTest demarrer() {
            try {
                return new DnsDeTest();
            } catch (java.net.SocketException e) {
                throw new IllegalStateException(e);
            }
        }

        @DynamicPropertySource
        static void dns(DynamicPropertyRegistry r) {
            r.add("vinasign.inscription.dns", DNS::adresse);
        }

        @Test
        void le_domaine_se_prouve_puis_la_plateforme_valide_ou_refuse() throws Exception {
            String domaine = "d" + UUID.randomUUID().toString().substring(0, 8) + ".exemple.ca";
            String courriel = "rh@" + domaine;
            assertEquals(422, poster("/api/v1/signup", Map.of("organisation", "Nord", "courriel", courriel, "langue", "fr")).getStatus(),
                    "domaine exigé en production");
            assertEquals(202, poster("/api/v1/signup", Map.of("organisation", "Nord", "courriel", courriel, "domaine", domaine, "langue", "fr")).getStatus());
            String jeton = jetonRecu(courriel);
            JsonNode aProuver = corps(poster("/api/v1/signup/confirmation", Map.of("jeton", jeton)));
            assertEquals("domaine-a-prouver", aProuver.get("etape").asText());
            assertEquals("_vinasign-verification." + domaine, aProuver.get("enregistrementDns").asText());
            MockHttpServletResponse absent = poster("/api/v1/signup/domain-verification", Map.of("jeton", jeton));
            assertEquals(409, absent.getStatus());
            assertTrue(corps(absent).get("type").asText().endsWith("domaine-non-prouve"));
            DNS.publier(aProuver.get("enregistrementDns").asText(), aProuver.get("valeurDns").asText());
            assertEquals("a-valider", corps(poster("/api/v1/signup/domain-verification", Map.of("jeton", jeton))).get("etape").asText());

            String id = aProuver.get("id").asText();
            assertEquals(0, new JdbcTemplate(proprietaire).queryForObject("SELECT count(*) FROM organisation WHERE id = ?::uuid", Integer.class, id),
                    "rien n'est créé avant la validation");
            JsonNode liste = corps(mvc.perform(MockMvcRequestBuilders.get("/api/v1/platform/signups").with(PLATEFORME)).andReturn().getResponse());
            assertTrue(liste.toString().contains(id));
            assertEquals(403, mvc.perform(MockMvcRequestBuilders.get("/api/v1/platform/signups").with(jwt())).andReturn().getResponse().getStatus());
            assertEquals("acceptee", corps(mvc.perform(MockMvcRequestBuilders.post("/api/v1/platform/signups/" + id + "/approval").with(PLATEFORME))
                    .andReturn().getResponse()).get("etape").asText());
            assertEquals(1, new JdbcTemplate(proprietaire).queryForObject("SELECT count(*) FROM organisation WHERE id = ?::uuid", Integer.class, id));

            String autre = "rh2@" + domaine;
            poster("/api/v1/signup", Map.of("organisation", "Nord bis", "courriel", autre, "domaine", domaine, "langue", "en"));
            String idAutre = corps(poster("/api/v1/signup/consultation", Map.of("jeton", jetonRecu(autre)))).get("id").asText();
            JsonNode refusee = corps(mvc.perform(MockMvcRequestBuilders.post("/api/v1/platform/signups/" + idAutre + "/rejection").with(PLATEFORME)
                    .contentType(MediaType.APPLICATION_JSON).content("{\"motif\":\"Doublon\"}")).andReturn().getResponse());
            assertEquals("refusee", refusee.get("etape").asText());
            assertEquals(404, mvc.perform(MockMvcRequestBuilders.post("/api/v1/platform/signups/" + UUID.randomUUID() + "/approval").with(PLATEFORME))
                    .andReturn().getResponse().getStatus());
        }
    }
}
