package ca.vinaitsolution.vinasign.enveloppe;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

import ca.vinaitsolution.vinasign.support.ApplicationsDeTest;
import ca.vinaitsolution.vinasign.support.ApplicationsDeTest.Application;
import ca.vinaitsolution.vinasign.support.InvitationsCapturees;
import ca.vinaitsolution.vinasign.support.PostgresDeTest;
import ca.vinaitsolution.vinasign.support.ReferentielsSql;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
 * VS-064 — liste paginée des enveloppes : chacun ne voit que son espace (application, ou portail de son organisation), les
 * plus récentes d'abord, sans doublon ni oubli d'une page à l'autre ; filtre par statut ; curseur et limite validés.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({PostgresDeTest.class, ApplicationsDeTest.class, ReferentielsSql.class, InvitationsCapturees.class})
class ListeEnveloppesApiTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired ApplicationsDeTest applications;

    private static JwtRequestPostProcessor utilisateur(UUID org, String role) {
        return jwt().jwt(j -> j.subject("utilisateur-" + UUID.randomUUID()).claim("org", org.toString())).authorities(new SimpleGrantedAuthority("ROLE_" + role));
    }

    private static JwtRequestPostProcessor application(Application app, String... portees) {
        return jwt().jwt(j -> j.subject(app.clientId()).claim("org", app.organisation().toString()).claim("client_id", app.clientId()))
                .authorities(List.of(portees).stream().<GrantedAuthority>map(p -> new SimpleGrantedAuthority("SCOPE_" + p)).toList());
    }

    private MockHttpServletResponse appel(MockHttpServletRequestBuilder r, JwtRequestPostProcessor qui, Object corps) throws Exception {
        r = r.with(qui).header("Idempotency-Key", UUID.randomUUID().toString());
        if (corps != null) r = r.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(corps));
        return mvc.perform(r).andReturn().getResponse();
    }

    private String creer(JwtRequestPostProcessor qui) throws Exception {
        MockHttpServletResponse r = appel(MockMvcRequestBuilders.post("/api/v1/envelopes"), qui, Map.of("externalReference", "L-" + UUID.randomUUID(),
                "mode", "empreinte", "expiresAt", Instant.now().plusSeconds(86400).toString()));
        assertEquals(201, r.getStatus(), r.getContentAsString());
        return json.readTree(r.getContentAsString()).get("id").asText();
    }

    private JsonNode lister(JwtRequestPostProcessor qui, String requete) throws Exception {
        MockHttpServletResponse r = appel(MockMvcRequestBuilders.get("/api/v1/envelopes" + requete), qui, null);
        assertEquals(200, r.getStatus(), r.getContentAsString());
        return json.readTree(r.getContentAsString());
    }

    @Test
    void chacun_voit_son_espace_page_par_page_sans_doublon() throws Exception {
        Application app = applications.enregistrer("enveloppes-lire", "enveloppes-creer");
        JwtRequestPostProcessor preparateur = utilisateur(app.organisation(), "preparateur");
        List<String> portail = new ArrayList<>();
        for (int i = 0; i < 5; i++) portail.add(creer(preparateur));
        String deLApplication = creer(application(app, "enveloppes-creer"));

        List<String> lues = new ArrayList<>();
        String requete = "?limite=2";
        int pages = 0;
        while (true) {
            JsonNode page = lister(utilisateur(app.organisation(), "auditeur"), requete);
            page.get("elements").forEach(e -> lues.add(e.get("id").asText()));
            pages++;
            if (page.get("suivant").isNull()) break;
            requete = "?limite=2&apres=" + page.get("suivant").asText();
        }
        assertEquals(3, pages);
        assertEquals(portail.reversed(), lues, "les plus récentes d'abord, chacune une fois");
        assertEquals(List.of(deLApplication), ids(lister(application(app, "enveloppes-lire"), "")), "l'application ne voit que les siennes");
        assertEquals(List.of(), ids(lister(utilisateur(applications.enregistrer("enveloppes-lire").organisation(), "preparateur"), "")),
                "rien d'une autre organisation");

        JsonNode premiere = lister(preparateur, "?limite=1").get("elements").get(0);
        assertEquals("brouillon", premiere.get("statut").asText());
        assertEquals(0, premiere.get("signataires").asInt());
        assertFalse(premiere.has("courriel"));
    }

    @Test
    void filtre_par_statut_et_valide_curseur_et_limite() throws Exception {
        UUID org = applications.enregistrer("enveloppes-lire").organisation();
        JwtRequestPostProcessor preparateur = utilisateur(org, "preparateur");
        String annulee = creer(preparateur);
        creer(preparateur);
        assertEquals(200, appel(MockMvcRequestBuilders.post("/api/v1/envelopes/" + annulee + "/cancel"), preparateur, null).getStatus());
        assertEquals(List.of(annulee), ids(lister(preparateur, "?statut=annulee")));
        assertEquals(1, ids(lister(preparateur, "?statut=brouillon")).size());
        assertEquals(List.of(), ids(lister(preparateur, "?statut=inconnu")));

        assertEquals(422, appel(MockMvcRequestBuilders.get("/api/v1/envelopes?apres=pas-un-curseur"), preparateur, null).getStatus());
        for (String limite : new String[] {"0", "101"}) {
            assertEquals(422, appel(MockMvcRequestBuilders.get("/api/v1/envelopes?limite=" + limite), preparateur, null).getStatus(), limite);
        }
        assertEquals(422, appel(MockMvcRequestBuilders.get("/api/v1/envelopes?statut=NON%20VALIDE"), preparateur, null).getStatus());
        assertEquals(422, appel(MockMvcRequestBuilders.get("/api/v1/envelopes?apres=" + "a".repeat(201)), preparateur, null).getStatus());
        assertEquals(403, appel(MockMvcRequestBuilders.get("/api/v1/envelopes"), utilisateur(org, "administrateur-organisation"), null).getStatus());
    }

    private static List<String> ids(JsonNode page) {
        List<String> ids = new ArrayList<>();
        page.get("elements").forEach(e -> ids.add(e.get("id").asText()));
        Set<String> uniques = new HashSet<>(ids);
        assertEquals(uniques.size(), ids.size());
        return ids;
    }
}
