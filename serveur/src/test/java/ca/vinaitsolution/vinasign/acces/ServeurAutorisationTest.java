package ca.vinaitsolution.vinasign.acces;

import static ca.vinaitsolution.vinasign.support.ApplicationsDeTest.assertion;
import static ca.vinaitsolution.vinasign.support.Fabrique.codeUnique;
import static ca.vinaitsolution.vinasign.support.Fabrique.type;
import static ca.vinaitsolution.vinasign.support.Fabrique.valeur;
import static org.junit.jupiter.api.Assertions.*;

import ca.vinaitsolution.vinasign.acces.infrastructure.securite.JetonsProprietes;
import ca.vinaitsolution.vinasign.support.ApplicationsDeTest;
import ca.vinaitsolution.vinasign.support.ApplicationsDeTest.Application;
import ca.vinaitsolution.vinasign.support.PostgresDeTest;
import ca.vinaitsolution.vinasign.support.ReferentielsSql;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/** VS-012 — serveur d'autorisation et API, sur la vraie pile et la vraie base. */
@SpringBootTest
@AutoConfigureMockMvc
@Import({PostgresDeTest.class, ApplicationsDeTest.class, ReferentielsSql.class})
class ServeurAutorisationTest {

    private static final String TYPE_ASSERTION = "urn:ietf:params:oauth:client-assertion-type:jwt-bearer";

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired ApplicationsDeTest applications;
    @Autowired ReferentielsSql sql;
    @Autowired JetonsProprietes proprietes;
    @Autowired JwtDecoder decodeur;
    @Autowired JWKSource<SecurityContext> clesServeur;

    private String assertionValide(Application a) {
        Instant t = Instant.now();
        return assertion(a.cle(), a.clientId(), a.clientId(), proprietes.emetteur(), UUID.randomUUID().toString(), t, t.plusSeconds(60));
    }

    private MockHttpServletResponse demander(Application a, String assertion, String portee) throws Exception {
        var r = MockMvcRequestBuilders.post("/oauth2/token").contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("grant_type", "client_credentials").param("client_id", a.clientId())
                .param("client_assertion_type", TYPE_ASSERTION).param("client_assertion", assertion);
        if (portee != null) r = r.param("scope", portee);
        return mvc.perform(r).andReturn().getResponse();
    }

    private String jeton(Application a, String portee) throws Exception {
        MockHttpServletResponse r = demander(a, assertionValide(a), portee);
        assertEquals(200, r.getStatus(), r.getContentAsString());
        return json.readTree(r.getContentAsString()).get("access_token").asText();
    }

    private MockHttpServletResponse api(String url, String jeton) throws Exception {
        var r = MockMvcRequestBuilders.get(url);
        if (jeton != null) r = r.header("Authorization", "Bearer " + jeton);
        return mvc.perform(r).andReturn().getResponse();
    }

    @Test
    void une_application_obtient_un_jeton_de_courte_duree_portant_audience_emetteur_portees_et_organisation() throws Exception {
        Application a = applications.enregistrer("enveloppes-lire", "enveloppes-creer");
        MockHttpServletResponse r = demander(a, assertionValide(a), "enveloppes-lire");
        assertEquals(200, r.getStatus(), r.getContentAsString());
        JsonNode corps = json.readTree(r.getContentAsString());
        assertEquals("Bearer", corps.get("token_type").asText());
        assertEquals(proprietes.duree().toSeconds(), corps.get("expires_in").asLong(), 1);
        Jwt jeton = decodeur.decode(corps.get("access_token").asText());
        assertEquals(List.of(proprietes.audience()), jeton.getAudience());
        assertEquals(proprietes.emetteur(), jeton.getIssuer().toString());
        assertEquals(a.organisation().toString(), jeton.getClaimAsString("org"));
        assertEquals(a.clientId(), jeton.getClaimAsString("client_id"));
        assertEquals(List.of("enveloppes-lire"), jeton.getClaimAsStringList("scope"));
        assertNotNull(jeton.getId(), "chaque jeton porte un jti");

        JsonNode vue = json.readTree(api("/api/v1/jeton", corps.get("access_token").asText()).getContentAsString());
        assertEquals(a.clientId(), vue.get("client").asText());
        assertEquals(a.organisation().toString(), vue.get("organisation").asText());
        assertEquals("enveloppes-lire", vue.get("portees").get(0).asText());
    }

    @Test
    void une_assertion_ne_sert_qu_une_fois() throws Exception {
        Application a = applications.enregistrer("enveloppes-lire");
        String assertion = assertionValide(a);
        assertEquals(200, demander(a, assertion, null).getStatus());
        MockHttpServletResponse rejeu = demander(a, assertion, null);
        assertEquals(401, rejeu.getStatus());
        assertEquals("invalid_client", json.readTree(rejeu.getContentAsString()).get("error").asText());
    }

    @Test
    void une_assertion_alteree_est_refusee() throws Exception {
        Application a = applications.enregistrer("enveloppes-lire");
        Instant t = Instant.now();
        String jti = UUID.randomUUID().toString();
        String autre = "essai-" + UUID.randomUUID();
        for (String mauvaise : List.of(
                assertion(a.cle(), a.clientId(), a.clientId(), "https://autre.example", jti + "1", t, t.plusSeconds(60)),
                assertion(a.cle(), a.clientId(), a.clientId(), proprietes.emetteur(), jti + "2", t.minusSeconds(600), t.minusSeconds(300)),
                assertion(ApplicationsDeTest.nouvelleCle(), a.clientId(), a.clientId(), proprietes.emetteur(), jti + "3", t, t.plusSeconds(60)),
                assertion(a.cle(), autre, a.clientId(), proprietes.emetteur(), jti + "4", t, t.plusSeconds(60)),
                assertion(a.cle(), a.clientId(), autre, proprietes.emetteur(), jti + "5", t, t.plusSeconds(60)),
                assertion(a.cle(), a.clientId(), a.clientId(), proprietes.emetteur(), null, t, t.plusSeconds(60)),
                assertion(a.cle(), a.clientId(), a.clientId(), proprietes.emetteur(), jti + "6", t, t.plusSeconds(3600)),
                assertion(a.cle(), a.clientId(), a.clientId(), proprietes.emetteur(), jti + "7", t.plusSeconds(300), t.plusSeconds(360)),
                "pas-un-jwt")) {
            assertEquals(401, demander(a, mauvaise, null).getStatus(), mauvaise);
        }
        Instant t2 = Instant.now();
        assertEquals(200, demander(a, assertion(a.cle(), a.clientId(), a.clientId(), proprietes.emetteur() + "/oauth2/token",
                UUID.randomUUID().toString(), t2, t2.plusSeconds(60)), null).getStatus(), "audience = point de jeton : acceptée");
    }

    @Test
    void un_client_inconnu_mal_forme_ou_revoque_est_refuse_et_une_portee_non_accordee_aussi() throws Exception {
        Application a = applications.enregistrer("enveloppes-lire");
        assertEquals(400, demander(a, assertionValide(a), "webhooks-gerer").getStatus(), "portée non accordée");
        Application inconnue = new Application("essai-" + UUID.randomUUID(), UUID.randomUUID(), ApplicationsDeTest.nouvelleCle());
        assertEquals(401, demander(inconnue, assertionValide(inconnue), null).getStatus());
        Application malFormee = new Application("x", UUID.randomUUID(), ApplicationsDeTest.nouvelleCle());
        assertEquals(401, demander(malFormee, assertionValide(malFormee), null).getStatus());
        applications.revoquer(a);
        assertEquals(401, demander(a, assertionValide(a), null).getStatus());
    }

    @Test
    void l_api_refuse_un_jeton_d_une_autre_audience_expire_falsifie_ou_absent() throws Exception {
        RSAKey cle = (RSAKey) clesServeur.get(new com.nimbusds.jose.jwk.JWKSelector(new com.nimbusds.jose.jwk.JWKMatcher.Builder().build()), null).get(0);
        UUID org = UUID.randomUUID();
        applications.organisationActive(org);
        Instant dans5 = Instant.now().plusSeconds(300);
        assertEquals(200, api("/api/v1/jeton", ApplicationsDeTest.jeton(cle, proprietes.emetteur(), List.of(proprietes.audience()), org, dans5)).getStatus(),
                "témoin : le même jeton, correct, est accepté");
        assertEquals(401, api("/api/v1/jeton", ApplicationsDeTest.jeton(cle, proprietes.emetteur(), List.of("https://autre.example"), org, dans5)).getStatus());
        assertEquals(401, api("/api/v1/jeton", ApplicationsDeTest.jeton(cle, "https://autre-emetteur.example", List.of(proprietes.audience()), org, dans5)).getStatus());
        assertEquals(401, api("/api/v1/jeton", ApplicationsDeTest.jeton(cle, proprietes.emetteur(), List.of(proprietes.audience()), org, Instant.now().minusSeconds(120))).getStatus());
        assertEquals(401, api("/api/v1/jeton", ApplicationsDeTest.jeton(ApplicationsDeTest.nouvelleCle(), proprietes.emetteur(), List.of(proprietes.audience()), org, dans5)).getStatus());
        MockHttpServletResponse sans = api("/api/v1/jeton", null);
        assertEquals(401, sans.getStatus());
        assertEquals(403, api("/api/v1/jeton", ApplicationsDeTest.jeton(cle, proprietes.emetteur(), List.of(proprietes.audience()),
                UUID.randomUUID(), dans5)).getStatus(), "un jeton valide pour une organisation inconnue est refusé (VS-010)");
    }

    @Test
    void l_organisation_du_jeton_commande_l_isolation_en_base() throws Exception {
        Application a = applications.enregistrer("enveloppes-lire");
        String code = codeUnique("par-organisation");
        sql.enregistrer(type(code, valeur("commune", 1, true)));
        sql.ajouterValeurOrganisation(code, a.organisation(), valeur("propre", 2, true));
        sql.ajouterValeurOrganisation(code, UUID.randomUUID(), valeur("autre-organisation", 3, true));
        String jeton = jeton(a, null);
        JsonNode avec = json.readTree(api("/api/v1/referentiels/" + code, jeton).getContentAsString()).get("valeurs");
        assertEquals(List.of("commune", "propre"), List.of(avec.get(0).get("code").asText(), avec.get(1).get("code").asText()));
        assertEquals(2, avec.size(), "jamais la valeur d'une autre organisation");
        assertEquals(1, json.readTree(api("/api/v1/referentiels/" + code, null).getContentAsString()).get("valeurs").size());
    }

    @Test
    void les_cles_publiees_ne_contiennent_aucune_partie_privee() throws Exception {
        MockHttpServletResponse r = api("/oauth2/jwks", null);
        assertEquals(200, r.getStatus());
        JWKSet cles = JWKSet.parse(r.getContentAsString());
        assertFalse(cles.getKeys().isEmpty());
        cles.getKeys().forEach(k -> assertFalse(k.isPrivate()));
        assertEquals(200, api("/.well-known/oauth-authorization-server", null).getStatus());
    }
}
