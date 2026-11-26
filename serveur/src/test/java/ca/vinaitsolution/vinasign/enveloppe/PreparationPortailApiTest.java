package ca.vinaitsolution.vinasign.enveloppe;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

import ca.vinaitsolution.vinasign.support.ApplicationsDeTest;
import ca.vinaitsolution.vinasign.support.ApplicationsDeTest.Application;
import ca.vinaitsolution.vinasign.support.InvitationsCapturees;
import ca.vinaitsolution.vinasign.support.PostgresDeTest;
import ca.vinaitsolution.vinasign.support.ReferentielsSql;
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
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/**
 * VS-064, VS-ADR-009 — un préparateur du portail prépare et envoie une enveloppe sans application cliente ; elle vit dans
 * l'espace « portail » de l'organisation, invisible des applications ; un autre rôle ne prépare pas.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({PostgresDeTest.class, ApplicationsDeTest.class, ReferentielsSql.class, InvitationsCapturees.class})
class PreparationPortailApiTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired ApplicationsDeTest applications;
    @Autowired InvitationsCapturees invitations;
    @Autowired @LiquibaseDataSource DataSource proprietaire;

    private static JwtRequestPostProcessor utilisateur(UUID org, String role) {
        return jwt().jwt(j -> j.subject("utilisateur-" + UUID.randomUUID()).claim("org", org.toString())).authorities(new SimpleGrantedAuthority("ROLE_" + role));
    }

    private MockHttpServletResponse appel(MockHttpServletRequestBuilder r, JwtRequestPostProcessor qui, Object corps) throws Exception {
        r = r.with(qui).header("Idempotency-Key", UUID.randomUUID().toString());
        if (corps != null) r = r.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(corps));
        return mvc.perform(r).andReturn().getResponse();
    }

    @Test
    void le_preparateur_envoie_depuis_le_portail_dans_l_espace_de_son_organisation() throws Exception {
        Application app = applications.enregistrer("enveloppes-lire", "enveloppes-creer");
        JwtRequestPostProcessor preparateur = utilisateur(app.organisation(), "preparateur");
        MockHttpServletResponse creee = appel(MockMvcRequestBuilders.post("/api/v1/envelopes"), preparateur, Map.of("externalReference", "P-" + UUID.randomUUID(),
                "mode", "empreinte", "expiresAt", Instant.now().plusSeconds(86400).toString()));
        assertEquals(201, creee.getStatus(), creee.getContentAsString());
        String id = json.readTree(creee.getContentAsString()).get("id").asText();
        assertEquals(200, appel(MockMvcRequestBuilders.put("/api/v1/envelopes/" + id + "/signers"), preparateur, List.of(Map.of("nom", "Zoé",
                "courriel", "zoe@exemple.ca", "langue", "fr", "type", "invite", "ordre", 1))).getStatus());
        assertEquals(200, appel(MockMvcRequestBuilders.post("/api/v1/envelopes/" + id + "/send"), preparateur, null).getStatus());
        assertEquals(1, invitations.pour(id).size(), "le signataire est invité");
        assertEquals(200, appel(MockMvcRequestBuilders.get("/api/v1/envelopes/" + id), utilisateur(app.organisation(), "preparateur"), null).getStatus(),
                "un autre préparateur de l'organisation la voit");

        JdbcTemplate base = new JdbcTemplate(proprietaire);
        assertEquals("portail", base.queryForObject("SELECT client_id FROM enveloppe WHERE id = ?::uuid", String.class, id));
        var jeton = jwt().jwt(j -> j.subject(app.clientId()).claim("org", app.organisation().toString()).claim("client_id", app.clientId()))
                .authorities(List.of("enveloppes-lire").stream().<GrantedAuthority>map(p -> new SimpleGrantedAuthority("SCOPE_" + p)).toList());
        assertEquals(404, appel(MockMvcRequestBuilders.get("/api/v1/envelopes/" + id), jeton, null).getStatus(), "invisible des applications");
        assertEquals(404, appel(MockMvcRequestBuilders.get("/api/v1/envelopes/" + id), utilisateur(applications.enregistrer("enveloppes-lire").organisation(),
                "preparateur"), null).getStatus(), "invisible d'une autre organisation");
    }

    @Test
    void deux_organisations_peuvent_utiliser_la_meme_reference_au_portail() throws Exception {
        String reference = "R-" + UUID.randomUUID();
        for (int i = 0; i < 2; i++) {
            UUID org = applications.enregistrer("enveloppes-lire").organisation();
            assertEquals(201, appel(MockMvcRequestBuilders.post("/api/v1/envelopes"), utilisateur(org, "preparateur"), Map.of("externalReference", reference,
                    "mode", "empreinte", "expiresAt", Instant.now().plusSeconds(86400).toString())).getStatus());
        }
    }

    @Test
    void un_autre_role_ne_prepare_pas() throws Exception {
        UUID org = applications.enregistrer("enveloppes-lire").organisation();
        for (String role : new String[] {"auditeur", "administrateur-organisation"}) {
            assertEquals(403, appel(MockMvcRequestBuilders.post("/api/v1/envelopes"), utilisateur(org, role), Map.of("externalReference", "X-1",
                    "mode", "empreinte", "expiresAt", Instant.now().plusSeconds(86400).toString())).getStatus(), role);
        }
    }

    /** L'auditeur lit les enveloppes du portail de son organisation et leur preuve, sans jamais rien y changer. */
    @Test
    void l_auditeur_lit_les_enveloppes_du_portail_sans_les_modifier() throws Exception {
        UUID org = applications.enregistrer("enveloppes-lire").organisation();
        String id = json.readTree(appel(MockMvcRequestBuilders.post("/api/v1/envelopes"), utilisateur(org, "preparateur"), Map.of("externalReference",
                "A-" + UUID.randomUUID(), "mode", "empreinte", "expiresAt", Instant.now().plusSeconds(86400).toString())).getContentAsString()).get("id").asText();
        JwtRequestPostProcessor auditeur = utilisateur(org, "auditeur");
        assertEquals(200, appel(MockMvcRequestBuilders.get("/api/v1/envelopes/" + id), auditeur, null).getStatus());
        assertEquals(200, appel(MockMvcRequestBuilders.get("/api/v1/envelopes/" + id + "/preuve/integrite"), auditeur, null).getStatus());
        assertEquals(403, appel(MockMvcRequestBuilders.put("/api/v1/envelopes/" + id + "/signers"), auditeur, List.of(Map.of("nom", "Zoé",
                "courriel", "zoe@exemple.ca", "langue", "fr", "type", "invite", "ordre", 1))).getStatus());
        assertEquals(403, appel(MockMvcRequestBuilders.post("/api/v1/envelopes/" + id + "/send"), auditeur, null).getStatus());
        assertEquals(403, appel(MockMvcRequestBuilders.post("/api/v1/envelopes/" + id + "/cancel"), auditeur, null).getStatus());
        assertEquals(404, appel(MockMvcRequestBuilders.get("/api/v1/envelopes/" + id), utilisateur(applications.enregistrer("enveloppes-lire").organisation(),
                "auditeur"), null).getStatus(), "invisible de l'auditeur d'une autre organisation");
        assertEquals(403, appel(MockMvcRequestBuilders.get("/api/v1/envelopes/" + id), utilisateur(org, "administrateur-organisation"), null).getStatus(),
                "l'administrateur n'est pas lecteur par défaut");
    }
}
