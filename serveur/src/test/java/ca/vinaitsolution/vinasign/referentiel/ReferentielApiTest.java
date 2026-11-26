package ca.vinaitsolution.vinasign.referentiel;

import static ca.vinaitsolution.vinasign.support.Fabrique.codeUnique;
import static ca.vinaitsolution.vinasign.support.Fabrique.type;
import static ca.vinaitsolution.vinasign.support.Fabrique.valeur;
import static org.junit.jupiter.api.Assertions.*;

import ca.vinaitsolution.vinasign.support.PostgresDeTest;
import ca.vinaitsolution.vinasign.support.ReferentielsSql;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/**
 * Référentiels sur la vraie pile et la vraie base. Les tests découvrent les référentiels par l'API au lieu de citer
 * des codes : ils restent vrais quand les valeurs initiales changent.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({PostgresDeTest.class, ReferentielsSql.class})
@TestPropertySource(properties = "vinasign.origines-autorisees=https://portail.exemple")
class ReferentielApiTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired ReferentielsSql sql;

    private MockHttpServletResponse get(String url, String... entetes) throws Exception {
        MockHttpServletRequestBuilder r = MockMvcRequestBuilders.get(url);
        for (int i = 0; i < entetes.length; i += 2) r = r.header(entetes[i], entetes[i + 1]);
        return mvc.perform(r).andReturn().getResponse();
    }

    private JsonNode corps(MockHttpServletResponse r) throws Exception {
        return json.readTree(r.getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
    }

    @Test
    void chaque_referentiel_annonce_est_consultable_et_non_vide() throws Exception {
        JsonNode liste = corps(get("/api/v1/referentiels"));
        assertTrue(liste.size() > 0, "les valeurs initiales sont chargées par Liquibase");
        for (JsonNode resume : liste) {
            MockHttpServletResponse r = get("/api/v1/referentiels/" + resume.get("code").asText());
            assertEquals(200, r.getStatus());
            JsonNode detail = corps(r);
            assertEquals(resume.get("nombreValeurs").asInt(), detail.get("valeurs").size());
            assertTrue(detail.get("valeurs").size() > 0, resume.get("code").asText() + " sans valeur active");
            detail.get("valeurs").forEach(v -> assertFalse(v.get("libelle").asText().isBlank()));
        }
    }

    @Test
    void une_valeur_ajoutee_en_base_apparait_sans_redeploiement_et_disparait_une_fois_retiree() throws Exception {
        String code = codeUnique("dynamique");
        sql.enregistrer(type(code, valeur("premiere", 1, true)));
        assertEquals(1, corps(get("/api/v1/referentiels/" + code)).get("valeurs").size());
        sql.ajouterValeur(code, valeur("ajoutee", 0, true));
        JsonNode apres = corps(get("/api/v1/referentiels/" + code)).get("valeurs");
        assertEquals("ajoutee", apres.get(0).get("code").asText(), "l'ordre en base pilote l'ordre affiché");
        sql.retirerValeur(code, "ajoutee");
        assertEquals(1, corps(get("/api/v1/referentiels/" + code)).get("valeurs").size());
    }

    @Test
    void la_langue_vient_du_parametre_puis_du_navigateur_puis_de_la_configuration() throws Exception {
        String code = codeUnique("langues");
        sql.enregistrer(type(code, valeur("v", 1, true)));
        String url = "/api/v1/referentiels/" + code;
        assertEquals("Kind " + code, corps(get(url + "?langue=en", "Accept-Language", "fr")).get("libelle").asText());
        assertEquals("Kind " + code, corps(get(url, "Accept-Language", "en-CA")).get("libelle").asText());
        assertEquals("Kind " + code, corps(get(url, "Accept-Language", "en-US,en;q=0.9,fr-CA;q=0.8,fr;q=0.7")).get("libelle").asText(),
                "liste pondérée d'un vrai navigateur : la langue de plus fort poids");
        assertEquals("Type " + code, corps(get(url, "Accept-Language", "fr-CA,fr;q=0.9,en-US;q=0.8,en;q=0.7")).get("libelle").asText());
        assertEquals("Type " + code, corps(get(url, "Accept-Language", "*")).get("libelle").asText(), "joker : langue principale");
        assertEquals("Type " + code, corps(get(url, "Accept-Language", "fr;q=x,,")).get("libelle").asText(),
                "en-tête mal formé : langue principale, jamais 400");
        assertEquals("Type " + code, corps(get(url)).get("libelle").asText());
        assertEquals("Type " + code, corps(mvc.perform(MockMvcRequestBuilders.get(url).param("langue", " ")).andReturn().getResponse())
                .get("libelle").asText(), "paramètre vide : ignoré");
        assertTrue(corps(get("/api/v1/referentiels?langue=en")).findValues("libelle").stream()
                .anyMatch(n -> n.asText().equals("Kind " + code)), "la liste est libellée dans la langue demandée");
    }

    @Test
    void un_referentiel_inconnu_ou_mal_nomme_rend_un_probleme_rfc_9457() throws Exception {
        MockHttpServletResponse inconnu = get("/api/v1/referentiels/" + codeUnique("absent"));
        assertEquals(404, inconnu.getStatus());
        assertTrue(inconnu.getContentType().startsWith("application/problem+json"));
        assertTrue(corps(inconnu).get("type").asText().endsWith("/referentiel-inconnu"));
        MockHttpServletResponse invalide = get("/api/v1/referentiels/MAJUSCULES");
        assertEquals(400, invalide.getStatus());
        assertTrue(corps(invalide).get("type").asText().endsWith("/requete-invalide"));
        assertEquals(400, get("/api/v1/referentiels?langue=francais").getStatus());
    }

    @Test
    void toute_autre_route_est_refusee_par_defaut_avec_les_entetes_de_securite() throws Exception {
        MockHttpServletResponse r = get("/api/v1/enveloppes");
        assertEquals(401, r.getStatus());
        MockHttpServletResponse ok = get("/api/v1/referentiels");
        assertEquals("nosniff", ok.getHeader("X-Content-Type-Options"));
        assertEquals("DENY", ok.getHeader("X-Frame-Options"));
        assertTrue(ok.getHeader("Content-Security-Policy").contains("default-src 'none'"));
        assertEquals("no-referrer", ok.getHeader("Referrer-Policy"));
        assertEquals("same-origin", ok.getHeader("Cross-Origin-Opener-Policy"));
        assertEquals("same-origin", ok.getHeader("Cross-Origin-Resource-Policy"));
        assertEquals(200, get("/v3/api-docs").getStatus());
        assertEquals(200, get("/actuator/health").getStatus());
    }

    @Test
    void seules_les_origines_configurees_obtiennent_cors() throws Exception {
        MockHttpServletResponse permise = mvc.perform(MockMvcRequestBuilders.options("/api/v1/referentiels")
                .header("Origin", "https://portail.exemple").header("Access-Control-Request-Method", "GET")).andReturn().getResponse();
        assertEquals("https://portail.exemple", permise.getHeader("Access-Control-Allow-Origin"));
        MockHttpServletResponse etrangere = mvc.perform(MockMvcRequestBuilders.options("/api/v1/referentiels")
                .header("Origin", "https://pirate.exemple").header("Access-Control-Request-Method", "GET")).andReturn().getResponse();
        assertEquals(403, etrangere.getStatus());
        assertNull(etrangere.getHeader("Access-Control-Allow-Origin"));
    }
}
