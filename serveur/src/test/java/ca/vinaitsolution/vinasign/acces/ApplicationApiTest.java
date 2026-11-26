package ca.vinaitsolution.vinasign.acces;

import static ca.vinaitsolution.vinasign.support.ApplicationsDeTest.assertion;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

import ca.vinaitsolution.vinasign.acces.infrastructure.securite.JetonsProprietes;
import ca.vinaitsolution.vinasign.support.ApplicationsDeTest;
import ca.vinaitsolution.vinasign.support.PostgresDeTest;
import ca.vinaitsolution.vinasign.support.ReferentielsSql;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.SignedJWT;
import java.time.Instant;
import java.util.List;
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

/** VS-011 — enregistrement d'une application cliente, jusqu'à son premier jeton, sur la vraie pile et la vraie base. */
@SpringBootTest
@AutoConfigureMockMvc
@Import({PostgresDeTest.class, ApplicationsDeTest.class, ReferentielsSql.class})
class ApplicationApiTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired ApplicationsDeTest donnees;
    @Autowired JetonsProprietes proprietes;
    @Autowired @LiquibaseDataSource DataSource proprietaire;

    /** Un environnement et une portée découverts par l'API des référentiels, jamais cités. */
    private String premierCode(String referentiel) throws Exception {
        MockHttpServletResponse r = mvc.perform(MockMvcRequestBuilders.get("/api/v1/referentiels/" + referentiel)).andReturn().getResponse();
        return json.readTree(r.getContentAsString()).get("valeurs").get(0).get("code").asText();
    }

    private static JwtRequestPostProcessor administrateurDe(UUID organisation) {
        return jwt().jwt(j -> j.subject("admin-" + organisation).claim("org", organisation.toString()))
                .authorities(new SimpleGrantedAuthority("ROLE_administrateur-organisation"));
    }

    private UUID organisation() {
        UUID id = UUID.randomUUID();
        donnees.organisationActive(id);
        return id;
    }

    private ObjectNode corps(RSAKey cle, String... origines) throws Exception {
        ObjectNode c = json.createObjectNode();
        c.put("nom", "Système RH");
        c.put("environnement", premierCode("environnement-application"));
        c.put("jwks", new JWKSet(cle.toPublicJWK()).toString());
        c.putArray("portees").add(premierCode("portee-api"));
        var o = c.putArray("origines");
        for (String origine : origines) o.add(origine);
        return c;
    }

    private MockHttpServletResponse enregistrer(Object corps, JwtRequestPostProcessor qui) throws Exception {
        return mvc.perform(MockMvcRequestBuilders.post("/api/v1/applications").with(qui).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(corps))).andReturn().getResponse();
    }

    private String type(MockHttpServletResponse r) throws Exception {
        return json.readTree(r.getContentAsString()).get("type").asText();
    }

    @Test
    void l_application_enregistree_obtient_un_jeton_de_son_organisation_et_de_son_environnement() throws Exception {
        UUID org = organisation();
        RSAKey cle = ApplicationsDeTest.nouvelleCle();
        ObjectNode demande = corps(cle, "https://rh.exemple.ca");
        MockHttpServletResponse r = enregistrer(demande, administrateurDe(org));
        assertEquals(201, r.getStatus(), r.getContentAsString());
        JsonNode vue = json.readTree(r.getContentAsString());
        String clientId = vue.get("clientId").asText();
        assertFalse(r.getContentAsString().contains("\"n\""), "la vue ne renvoie aucune clé");
        assertEquals(List.of("https://rh.exemple.ca"), List.of(vue.get("origines").get(0).asText()));

        Instant t = Instant.now();
        MockHttpServletResponse jeton = mvc.perform(MockMvcRequestBuilders.post("/oauth2/token").contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("grant_type", "client_credentials").param("client_id", clientId)
                .param("client_assertion_type", "urn:ietf:params:oauth:client-assertion-type:jwt-bearer")
                .param("client_assertion", assertion(cle, clientId, clientId, proprietes.emetteur(), UUID.randomUUID().toString(), t,
                        t.plusSeconds(60)))).andReturn().getResponse();
        assertEquals(200, jeton.getStatus(), jeton.getContentAsString());
        var revendications = SignedJWT.parse(json.readTree(jeton.getContentAsString()).get("access_token").asText()).getJWTClaimsSet();
        assertEquals(org.toString(), revendications.getStringClaim("org"));
        assertEquals(demande.get("environnement").asText(), revendications.getStringClaim("env"));

        assertEquals(1, new JdbcTemplate(proprietaire).queryForObject(
                "SELECT count(*) FROM journal_audit WHERE tenant_id = ? AND action = 'application-enregistree' AND cible = ?",
                Integer.class, org, clientId));
    }

    @Test
    void une_cle_ne_sert_qu_a_une_application_meme_d_une_autre_organisation_ou_d_un_autre_environnement() throws Exception {
        RSAKey cle = ApplicationsDeTest.nouvelleCle();
        assertEquals(201, enregistrer(corps(cle), administrateurDe(organisation())).getStatus());
        MockHttpServletResponse doublon = enregistrer(corps(cle), administrateurDe(organisation()));
        assertEquals(409, doublon.getStatus());
        assertTrue(type(doublon).endsWith("/cle-deja-enregistree"));
    }

    @Test
    void chaque_organisation_ne_liste_que_ses_applications() throws Exception {
        UUID a = organisation();
        UUID b = organisation();
        enregistrer(corps(ApplicationsDeTest.nouvelleCle()), administrateurDe(a));
        enregistrer(corps(ApplicationsDeTest.nouvelleCle()), administrateurDe(b));
        JsonNode liste = json.readTree(mvc.perform(MockMvcRequestBuilders.get("/api/v1/applications").with(administrateurDe(a)))
                .andReturn().getResponse().getContentAsString());
        assertEquals(1, liste.size());
    }

    @Test
    void les_saisies_invalides_sont_refusees_avec_un_type_qui_dit_quoi_corriger() throws Exception {
        UUID org = organisation();
        ObjectNode privee = corps(ApplicationsDeTest.nouvelleCle());
        privee.put("jwks", new JWKSet(ApplicationsDeTest.nouvelleCle()).toString(false));
        MockHttpServletResponse r = enregistrer(privee, administrateurDe(org));
        assertEquals(422, r.getStatus());
        assertTrue(type(r).endsWith("/cles-publiques-invalides"));

        assertEquals(400, enregistrer(corps(ApplicationsDeTest.nouvelleCle(), "http://rh.exemple.ca"), administrateurDe(org)).getStatus());

        ObjectNode portee = corps(ApplicationsDeTest.nouvelleCle());
        portee.putArray("portees").add("tout-faire");
        MockHttpServletResponse p = enregistrer(portee, administrateurDe(org));
        assertEquals(422, p.getStatus());
        assertTrue(type(p).endsWith("/valeur-referentiel-inconnue"));

        ObjectNode environnement = corps(ApplicationsDeTest.nouvelleCle());
        environnement.put("environnement", "recette");
        assertEquals(422, enregistrer(environnement, administrateurDe(org)).getStatus());
    }

    @Test
    void seul_un_administrateur_d_organisation_enregistre_et_au_nom_d_une_organisation() throws Exception {
        RSAKey cle = ApplicationsDeTest.nouvelleCle();
        JwtRequestPostProcessor sansOrganisation = jwt().authorities(new SimpleGrantedAuthority("ROLE_administrateur-organisation"));
        MockHttpServletResponse r = enregistrer(corps(cle), sansOrganisation);
        assertEquals(403, r.getStatus());
        assertTrue(type(r).endsWith("/organisation-requise"));
        JwtRequestPostProcessor application = jwt().jwt(j -> j.claim("org", organisation().toString()))
                .authorities(new SimpleGrantedAuthority("SCOPE_enveloppes-creer"));
        assertEquals(403, enregistrer(corps(cle), application).getStatus());
    }

    private String enregistrerPour(UUID org, RSAKey cle) throws Exception {
        MockHttpServletResponse r = enregistrer(corps(cle), administrateurDe(org));
        assertEquals(201, r.getStatus(), r.getContentAsString());
        return json.readTree(r.getContentAsString()).get("clientId").asText();
    }

    private MockHttpServletResponse demanderJeton(String clientId, RSAKey cle) throws Exception {
        Instant t = Instant.now();
        return mvc.perform(MockMvcRequestBuilders.post("/oauth2/token").contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("grant_type", "client_credentials").param("client_id", clientId)
                .param("client_assertion_type", "urn:ietf:params:oauth:client-assertion-type:jwt-bearer")
                .param("client_assertion", assertion(cle, clientId, clientId, proprietes.emetteur(), UUID.randomUUID().toString(), t,
                        t.plusSeconds(60)))).andReturn().getResponse();
    }

    private MockHttpServletResponse api(String jeton) throws Exception {
        return mvc.perform(MockMvcRequestBuilders.get("/api/v1/jeton").header("Authorization", "Bearer " + jeton)).andReturn().getResponse();
    }

    private MockHttpServletResponse renouveler(UUID org, String clientId, RSAKey... cles) throws Exception {
        List<com.nimbusds.jose.jwk.JWK> publiques = java.util.Arrays.stream(cles).map(c -> (com.nimbusds.jose.jwk.JWK) c.toPublicJWK()).toList();
        ObjectNode corps = json.createObjectNode().put("jwks", new JWKSet(publiques).toString());
        return mvc.perform(MockMvcRequestBuilders.put("/api/v1/applications/" + clientId + "/cles").with(administrateurDe(org))
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(corps))).andReturn().getResponse();
    }

    @Test
    void une_revocation_refuse_aussitot_les_jetons_deja_emis_et_tout_nouveau_jeton() throws Exception {
        UUID org = organisation();
        RSAKey cle = ApplicationsDeTest.nouvelleCle();
        String clientId = enregistrerPour(org, cle);
        String jeton = json.readTree(demanderJeton(clientId, cle).getContentAsString()).get("access_token").asText();
        assertEquals(200, api(jeton).getStatus());

        MockHttpServletResponse r = mvc.perform(MockMvcRequestBuilders.post("/api/v1/applications/" + clientId + "/revocation")
                .with(administrateurDe(org))).andReturn().getResponse();
        assertEquals(200, r.getStatus(), r.getContentAsString());
        assertEquals("revoquee", json.readTree(r.getContentAsString()).get("statut").asText());

        MockHttpServletResponse refus = api(jeton);
        assertEquals(403, refus.getStatus(), "jeton encore valide, mais application révoquée : refusé dès la requête suivante");
        assertTrue(type(refus).endsWith("/application-revoquee"));
        assertEquals(401, demanderJeton(clientId, cle).getStatus());
        assertEquals(409, mvc.perform(MockMvcRequestBuilders.post("/api/v1/applications/" + clientId + "/revocation")
                .with(administrateurDe(org))).andReturn().getResponse().getStatus());
    }

    @Test
    void pendant_une_rotation_les_deux_cles_servent_puis_seule_la_nouvelle() throws Exception {
        UUID org = organisation();
        RSAKey ancienne = ApplicationsDeTest.nouvelleCle();
        RSAKey nouvelle = ApplicationsDeTest.nouvelleCle();
        String clientId = enregistrerPour(org, ancienne);
        assertEquals(401, demanderJeton(clientId, nouvelle).getStatus(), "avant la rotation, la nouvelle clé est inconnue");

        assertEquals(200, renouveler(org, clientId, ancienne, nouvelle).getStatus());
        assertEquals(200, demanderJeton(clientId, ancienne).getStatus());
        assertEquals(200, demanderJeton(clientId, nouvelle).getStatus());

        assertEquals(200, renouveler(org, clientId, nouvelle).getStatus());
        assertEquals(401, demanderJeton(clientId, ancienne).getStatus(), "l'ancienne clé ne sert plus");
        assertEquals(200, demanderJeton(clientId, nouvelle).getStatus());

        assertEquals(2, new JdbcTemplate(proprietaire).queryForObject(
                "SELECT count(*) FROM journal_audit WHERE cible = ? AND action = 'cles-application-renouvelees'", Integer.class, clientId));
    }

    @Test
    void une_rotation_n_accepte_ni_trois_cles_ni_la_cle_d_une_autre_application_ni_une_autre_organisation() throws Exception {
        UUID org = organisation();
        RSAKey cle = ApplicationsDeTest.nouvelleCle();
        String clientId = enregistrerPour(org, cle);
        assertEquals(422, renouveler(org, clientId, cle, ApplicationsDeTest.nouvelleCle(), ApplicationsDeTest.nouvelleCle()).getStatus());
        RSAKey autre = ApplicationsDeTest.nouvelleCle();
        enregistrerPour(organisation(), autre);
        assertEquals(409, renouveler(org, clientId, cle, autre).getStatus());
        MockHttpServletResponse ailleurs = renouveler(organisation(), clientId, ApplicationsDeTest.nouvelleCle());
        assertEquals(404, ailleurs.getStatus());
        assertTrue(type(ailleurs).endsWith("/application-introuvable"));
        assertEquals(200, demanderJeton(clientId, cle).getStatus(), "les refus n'ont rien changé");
    }
}
