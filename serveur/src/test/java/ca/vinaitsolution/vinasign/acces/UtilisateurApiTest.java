package ca.vinaitsolution.vinasign.acces;

import static org.junit.jupiter.api.Assertions.*;

import ca.vinaitsolution.vinasign.acces.infrastructure.securite.PortailProprietes;
import ca.vinaitsolution.vinasign.support.ApplicationsDeTest;
import ca.vinaitsolution.vinasign.support.FournisseurIdentiteDeTest;
import ca.vinaitsolution.vinasign.support.FournisseurIdentiteDeTest.Personne;
import ca.vinaitsolution.vinasign.support.PostgresDeTest;
import ca.vinaitsolution.vinasign.support.ReferentielsSql;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.liquibase.LiquibaseDataSource;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/**
 * VS-014 — utilisateurs du portail, de bout en bout : jetons signés par un fournisseur OIDC d'essai, vrai résolveur
 * d'émetteurs, vraie base. Rien n'est simulé côté VinaSign.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({PostgresDeTest.class, ApplicationsDeTest.class, ReferentielsSql.class, FournisseurIdentiteDeTest.class})
class UtilisateurApiTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired PortailProprietes portail;
    @Autowired @LiquibaseDataSource DataSource proprietaire;

    private Personne plateforme;
    private UUID organisation;

    /** L'administrateur de la plateforme est créé par l'exploitation (compte propriétaire), puis tout passe par l'API. */
    @BeforeEach
    void preparer() throws Exception {
        plateforme = Personne.avecMfa(courriel("plateforme"));
        new JdbcTemplate(proprietaire).update("INSERT INTO utilisateur_portail (id, courriel, roles) VALUES (?, ?, ARRAY['administrateur-plateforme'])",
                UUID.randomUUID(), plateforme.courriel());
        MockHttpServletResponse r = appel(HttpMethod.POST, "/api/v1/organisations", plateforme, "{\"nom\":\"Clinique\"}");
        assertEquals(201, r.getStatus(), r.getContentAsString());
        organisation = UUID.fromString(corps(r).get("id").asText());
    }

    private static String courriel(String prefixe) {
        return prefixe + "-" + UUID.randomUUID() + "@exemple.ca";
    }

    private MockHttpServletResponse appel(HttpMethod methode, String url, Personne qui, String corps) throws Exception {
        var r = MockMvcRequestBuilders.request(methode, url).header("Authorization", "Bearer " + FournisseurIdentiteDeTest.jeton(portail, qui));
        if (corps != null) r = r.contentType(MediaType.APPLICATION_JSON).content(corps);
        return mvc.perform(r).andReturn().getResponse();
    }

    private JsonNode corps(MockHttpServletResponse r) throws Exception {
        return json.readTree(r.getContentAsString());
    }

    private Personne administrateur() throws Exception {
        Personne admin = Personne.avecMfa(courriel("admin"));
        assertEquals(201, appel(HttpMethod.POST, "/api/v1/organisations/" + organisation + "/administrateurs", plateforme,
                "{\"courriel\":\"" + admin.courriel().toUpperCase() + "\"}").getStatus());
        return admin;
    }

    private UUID inviter(Personne admin, Personne invite, String... roles) throws Exception {
        String liste = String.join("\",\"", roles);
        MockHttpServletResponse r = appel(HttpMethod.POST, "/api/v1/utilisateurs", admin,
                "{\"courriel\":\"" + invite.courriel() + "\",\"roles\":[\"" + liste + "\"]}");
        assertEquals(201, r.getStatus(), r.getContentAsString());
        return UUID.fromString(corps(r).get("id").asText());
    }

    @Test
    void l_administrateur_invite_se_lie_a_sa_premiere_connexion_et_agit_pour_son_organisation() throws Exception {
        Personne admin = administrateur();
        MockHttpServletResponse moi = appel(HttpMethod.GET, "/api/v1/moi", admin, null);
        assertEquals(200, moi.getStatus(), moi.getContentAsString());
        assertEquals(organisation.toString(), corps(moi).get("organisation").asText());
        assertEquals(List.of("administrateur-organisation"), List.of(corps(moi).get("roles").get(0).asText()));
        assertTrue(corps(moi).get("acteur").asText().startsWith("utilisateur:"), "l'acteur est opaque, jamais le courriel");

        Personne preparateur = Personne.sansMfa(courriel("prep"));
        inviter(admin, preparateur, "preparateur");
        JsonNode liste = corps(appel(HttpMethod.GET, "/api/v1/utilisateurs", admin, null));
        assertEquals(2, liste.size(), "l'administrateur et le préparateur, rien d'une autre organisation");
        assertEquals(List.of("preparateur"), List.of(corps(appel(HttpMethod.GET, "/api/v1/moi", preparateur, null)).get("roles").get(0).asText()),
                "un rôle sans exigence de MFA est accordé sans MFA");
    }

    @Test
    void sans_mfa_attestee_le_role_d_administration_n_est_pas_accorde() throws Exception {
        Personne admin = administrateur();
        Personne sansMfa = admin.avecAmr(List.of("pwd"));
        assertEquals(0, corps(appel(HttpMethod.GET, "/api/v1/moi", sansMfa, null)).get("roles").size());
        assertEquals(403, appel(HttpMethod.GET, "/api/v1/utilisateurs", sansMfa, null).getStatus());
        assertEquals(200, appel(HttpMethod.GET, "/api/v1/utilisateurs", admin.avecAmr(List.of("hwk")), null).getStatus(),
                "une clé matérielle (hwk) est une MFA");
    }

    @Test
    void une_identite_inconnue_non_verifiee_ou_d_un_autre_emetteur_est_refusee() throws Exception {
        assertEquals(401, appel(HttpMethod.GET, "/api/v1/moi", Personne.avecMfa(courriel("inconnu")), null).getStatus());
        Personne admin = administrateur();
        Personne nonVerifie = new Personne("sujet-" + UUID.randomUUID(), admin.courriel(), false, List.of("mfa"));
        assertEquals(401, appel(HttpMethod.GET, "/api/v1/moi", nonVerifie, null).getStatus(), "un courriel non attesté ne lie rien");
        String autre = FournisseurIdentiteDeTest.jeton("https://autre-idp.exemple", portail.audience(), admin);
        assertEquals(401, mvc.perform(MockMvcRequestBuilders.get("/api/v1/moi").header("Authorization", "Bearer " + autre))
                .andReturn().getResponse().getStatus());
        String audience = FournisseurIdentiteDeTest.jeton(portail.emetteur(), "api://autre", admin);
        assertEquals(401, mvc.perform(MockMvcRequestBuilders.get("/api/v1/moi").header("Authorization", "Bearer " + audience))
                .andReturn().getResponse().getStatus());
    }

    @Test
    void une_identite_deja_liee_ne_peut_pas_etre_reprise_par_un_autre_sujet() throws Exception {
        Personne admin = administrateur();
        assertEquals(200, appel(HttpMethod.GET, "/api/v1/moi", admin, null).getStatus());
        Personne usurpateur = Personne.avecMfa(admin.courriel());
        assertEquals(401, appel(HttpMethod.GET, "/api/v1/moi", usurpateur, null).getStatus(),
                "un autre sujet portant le même courriel ne prend pas le compte déjà lié");
    }

    @Test
    void roles_modifies_desactivation_et_audit() throws Exception {
        Personne admin = administrateur();
        Personne auditeur = Personne.sansMfa(courriel("audit"));
        UUID id = inviter(admin, auditeur, "auditeur");
        assertEquals(200, appel(HttpMethod.GET, "/api/v1/moi", auditeur, null).getStatus());

        MockHttpServletResponse plateformeRefusee = appel(HttpMethod.PUT, "/api/v1/utilisateurs/" + id + "/roles", admin,
                "{\"roles\":[\"administrateur-plateforme\"]}");
        assertEquals(422, plateformeRefusee.getStatus());
        assertTrue(corps(plateformeRefusee).get("type").asText().endsWith("/role-non-attribuable"));
        assertEquals(200, appel(HttpMethod.PUT, "/api/v1/utilisateurs/" + id + "/roles", admin, "{\"roles\":[\"preparateur\"]}").getStatus());
        assertEquals("preparateur", corps(appel(HttpMethod.GET, "/api/v1/moi", auditeur, null)).get("roles").get(0).asText());

        assertEquals(200, appel(HttpMethod.POST, "/api/v1/utilisateurs/" + id + "/desactivation", admin, null).getStatus());
        assertEquals(401, appel(HttpMethod.GET, "/api/v1/moi", auditeur, null).getStatus(), "désactivé : refusé à la requête suivante");
        assertEquals(409, appel(HttpMethod.POST, "/api/v1/utilisateurs/" + id + "/desactivation", admin, null).getStatus());

        List<String> actions = new JdbcTemplate(proprietaire).queryForList(
                "SELECT action FROM journal_audit WHERE cible = ? ORDER BY survenu_le", String.class, id.toString());
        assertEquals(List.of("utilisateur-invite", "roles-utilisateur-modifies", "utilisateur-desactive"), actions);
    }

    @Test
    void les_saisies_invalides_et_les_doublons_sont_refuses() throws Exception {
        Personne admin = administrateur();
        assertEquals(409, appel(HttpMethod.POST, "/api/v1/organisations/" + organisation + "/administrateurs", plateforme,
                "{\"courriel\":\"" + admin.courriel() + "\"}").getStatus(), "un courriel n'est inscrit qu'une fois");
        assertEquals(400, appel(HttpMethod.POST, "/api/v1/utilisateurs", admin, "{\"courriel\":\"pas-un-courriel\",\"roles\":[\"auditeur\"]}")
                .getStatus());
        assertEquals(422, appel(HttpMethod.POST, "/api/v1/utilisateurs", admin,
                "{\"courriel\":\"" + courriel("x") + "\",\"roles\":[\"inconnu\"]}").getStatus());
        assertEquals(404, appel(HttpMethod.PUT, "/api/v1/utilisateurs/" + UUID.randomUUID() + "/roles", admin,
                "{\"roles\":[\"auditeur\"]}").getStatus());
        assertEquals(409, appel(HttpMethod.POST, "/api/v1/organisations/" + UUID.randomUUID() + "/administrateurs", plateforme,
                "{\"courriel\":\"" + courriel("y") + "\"}").getStatus(), "pas d'invitation dans une organisation inconnue");
        assertEquals(403, appel(HttpMethod.POST, "/api/v1/organisations/" + organisation + "/administrateurs", admin,
                "{\"courriel\":\"" + courriel("z") + "\"}").getStatus(), "réservé à la plateforme");
    }

    @Test
    void la_suspension_de_l_organisation_coupe_aussi_ses_utilisateurs() throws Exception {
        Personne admin = administrateur();
        assertEquals(200, appel(HttpMethod.GET, "/api/v1/utilisateurs", admin, null).getStatus());
        appel(HttpMethod.POST, "/api/v1/organisations/" + organisation + "/suspension", plateforme, null);
        MockHttpServletResponse r = appel(HttpMethod.GET, "/api/v1/utilisateurs", admin, null);
        assertEquals(403, r.getStatus());
        assertTrue(corps(r).get("type").asText().endsWith("/organisation-suspendue"));
    }
}
