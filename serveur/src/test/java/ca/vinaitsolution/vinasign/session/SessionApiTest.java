package ca.vinaitsolution.vinasign.session;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

import ca.vinaitsolution.vinasign.support.ApplicationsDeTest;
import ca.vinaitsolution.vinasign.support.ApplicationsDeTest.Application;
import ca.vinaitsolution.vinasign.support.InvitationsCapturees;
import ca.vinaitsolution.vinasign.support.PostgresDeTest;
import ca.vinaitsolution.vinasign.support.ReferentielsSql;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/** VS-040, VS-041 — lien sécurisé, session du signataire, consentement versionné, sur la vraie pile et la vraie base. */
@SpringBootTest
@AutoConfigureMockMvc
@Import({PostgresDeTest.class, ApplicationsDeTest.class, ReferentielsSql.class, InvitationsCapturees.class})
class SessionApiTest {

    private static final List<String> PORTEES = List.of("enveloppes-creer", "enveloppes-lire", "enveloppes-envoyer", "signataires-gerer");

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired ApplicationsDeTest applications;
    @Autowired InvitationsCapturees invitations;
    @Autowired ca.vinaitsolution.vinasign.notification.application.ExpedierSms expedierSms;
    @Autowired ca.vinaitsolution.vinasign.session.application.RelancerSignataires relances;
    @Autowired @LiquibaseDataSource DataSource proprietaire;

    private Application app;

    @BeforeEach
    void preparer() {
        app = applications.enregistrer(PORTEES.toArray(String[]::new));
    }

    private JwtRequestPostProcessor jeton() {
        return jwt().jwt(j -> j.subject(app.clientId()).claim("org", app.organisation().toString()).claim("client_id", app.clientId()))
                .authorities(PORTEES.stream().<GrantedAuthority>map(p -> new SimpleGrantedAuthority("SCOPE_" + p)).toList());
    }

    private JsonNode corps(MockHttpServletResponse r) throws Exception {
        return json.readTree(r.getContentAsString(StandardCharsets.UTF_8));
    }

    private String valeur(String referentiel, String code) throws Exception {
        for (JsonNode v : corps(mvc.perform(MockMvcRequestBuilders.get("/api/v1/referentiels/" + referentiel)).andReturn().getResponse()).get("valeurs")) {
            if (code == null || v.get("code").asText().equals(code)) return v.get("code").asText();
        }
        throw new IllegalStateException(referentiel);
    }

    /** Enveloppe en mode empreinte, deux signataires invités dans l'ordre, envoyée ; rend son identifiant. */
    private String envoyee() throws Exception {
        String corpsEnveloppe = json.writeValueAsString(Map.of("externalReference", "S-" + UUID.randomUUID(),
                "mode", valeur("mode-enveloppe", "empreinte"), "expiresAt", Instant.now().plusSeconds(86400).toString()));
        String id = corps(mvc.perform(MockMvcRequestBuilders.post("/api/v1/envelopes").with(jeton()).header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON).content(corpsEnveloppe)).andReturn().getResponse()).get("id").asText();
        String langue = valeur("langue", "fr");
        String type = valeur("type-signataire", "invite");
        String signataires = json.writeValueAsString(List.of(
                Map.of("nom", "Alice", "courriel", "alice@exemple.ca", "langue", langue, "type", type, "ordre", 1),
                Map.of("nom", "Bruno", "courriel", "bruno@exemple.ca", "langue", langue, "type", type, "ordre", 2)));
        mvc.perform(MockMvcRequestBuilders.put("/api/v1/envelopes/" + id + "/signers").with(jeton()).contentType(MediaType.APPLICATION_JSON).content(signataires));
        assertEquals(200, mvc.perform(MockMvcRequestBuilders.post("/api/v1/envelopes/" + id + "/send").with(jeton())).andReturn().getResponse().getStatus());
        return id;
    }

    private MockHttpServletResponse ouvrir(String jetonLien) throws Exception {
        return mvc.perform(MockMvcRequestBuilders.post("/api/v1/signataire/ouverture").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("jeton", jetonLien)))).andReturn().getResponse();
    }

    private MockHttpServletResponse consentir(String session, String version) throws Exception {
        var r = MockMvcRequestBuilders.post("/api/v1/signataire/consentement").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("version", version)));
        if (session != null) r = r.header("X-Session-Signataire", session);
        return mvc.perform(r).andReturn().getResponse();
    }

    @Test
    void a_l_envoi_seul_le_premier_signataire_recoit_un_lien_qui_ouvre_une_session() throws Exception {
        String id = envoyee();
        List<Map<String, String>> recues = invitations.pour(id);
        assertEquals(1, recues.size(), "seul l'ordre 1 est invité");
        String jetonLien = recues.get(0).get("jeton");
        assertTrue(jetonLien.length() >= 43, "256 bits");
        assertEquals(0, new JdbcTemplate(proprietaire).queryForObject("SELECT count(*) FROM lien_signature WHERE empreinte_jeton = ?",
                Integer.class, jetonLien), "le jeton en clair n'est jamais conservé");

        MockHttpServletResponse r = ouvrir(jetonLien);
        assertEquals(200, r.getStatus(), r.getContentAsString());
        JsonNode session = corps(r);
        assertEquals("Alice", session.get("nom").asText());
        assertTrue(session.get("peutAgir").asBoolean());
        assertFalse(session.get("aConsenti").asBoolean());
        assertFalse(session.get("texteConsentement").asText().isBlank());

        String version = session.get("versionConsentement").asText();
        String jetonSession = session.get("jetonSession").asText();
        MockHttpServletResponse accepte = consentir(jetonSession, version);
        assertEquals(200, accepte.getStatus(), accepte.getContentAsString());
        assertEquals(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(session.get("texteConsentement").asText()
                .getBytes(StandardCharsets.UTF_8))), corps(accepte).get("empreinteTexte").asText(), "l'empreinte du texte affiché");
        assertTrue(corps(ouvrir(jetonLien)).get("aConsenti").asBoolean(), "le consentement est retenu");
        assertEquals(409, consentir(jetonSession, "version-ancienne").getStatus());
    }

    @Test
    void un_lien_inconnu_rend_404_un_lien_consomme_410_et_une_session_absente_401() throws Exception {
        assertEquals(404, ouvrir("inconnu-" + UUID.randomUUID()).getStatus());
        assertEquals(400, ouvrir("x".repeat(101)).getStatus(), "jeton démesuré refusé à la validation");
        String id = envoyee();
        String jetonLien = invitations.pour(id).get(0).get("jeton");
        new JdbcTemplate(proprietaire).update("UPDATE lien_signature SET consomme_le = now() WHERE enveloppe_id = ?::uuid", id);
        MockHttpServletResponse rejoue = ouvrir(jetonLien);
        assertEquals(410, rejoue.getStatus(), "un lien rejoué après la signature");
        assertTrue(corps(rejoue).get("type").asText().endsWith("/lien-consomme"));
        assertEquals(401, consentir(null, "v").getStatus());
        assertEquals(401, consentir("session-inconnue", "v").getStatus());
        assertEquals(401, consentir("y".repeat(101), "v").getStatus());
    }

    @Test
    void l_annulation_revoque_les_liens_de_l_enveloppe() throws Exception {
        String id = envoyee();
        String jetonLien = invitations.pour(id).get(0).get("jeton");
        assertEquals(200, mvc.perform(MockMvcRequestBuilders.post("/api/v1/envelopes/" + id + "/cancel").with(jeton())).andReturn().getResponse().getStatus());
        assertEquals(404, ouvrir(jetonLien).getStatus(), "lien révoqué : invalide");
    }

    @Test
    void une_session_expiree_n_authentifie_plus() throws Exception {
        String id = envoyee();
        JsonNode session = corps(ouvrir(invitations.pour(id).get(0).get("jeton")));
        new JdbcTemplate(proprietaire).update("UPDATE session_signataire SET expire_le = now() - interval '1 second' WHERE enveloppe_id = ?::uuid", id);
        assertEquals(401, consentir(session.get("jetonSession").asText(), session.get("versionConsentement").asText()).getStatus());
    }

    @Test
    void le_code_recu_par_courriel_eleve_la_session_et_cinq_erreurs_verrouillent() throws Exception {
        String id = envoyee();
        JsonNode session = corps(ouvrir(invitations.pour(id).get(0).get("jeton")));
        String jetonSession = session.get("jetonSession").asText();
        String canal = valeur("canal-code", "courriel");
        assertEquals(202, mvc.perform(MockMvcRequestBuilders.post("/api/v1/signataire/code").header("X-Session-Signataire", jetonSession)
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("canal", canal)))).andReturn().getResponse().getStatus());
        String corpsCourriel = new JdbcTemplate(proprietaire).queryForObject(
                "SELECT corps FROM courriel_sortant WHERE destinataire = 'alice@exemple.ca' AND sujet LIKE '%code%' ORDER BY cree_le DESC LIMIT 1", String.class);
        String code = corpsCourriel.replaceAll("(?s).*?(\\d{6}).*", "$1");
        assertEquals(6, code.length(), "le code figure dans le courriel");
        assertEquals(0, new JdbcTemplate(proprietaire).queryForObject("SELECT count(*) FROM defi_code WHERE empreinte_code LIKE ?", Integer.class,
                "%" + code + "%"), "le code n'est jamais conservé en clair");
        for (int i = 0; i < 4; i++) {
            JsonNode r = corps(verifier(jetonSession, "000000".equals(code) ? "111111" : "000000"));
            assertFalse(r.get("valide").asBoolean());
            assertEquals(4 - i, r.get("essaisRestants").asInt());
        }
        JsonNode bon = corps(verifier(jetonSession, code));
        assertTrue(bon.get("valide").asBoolean(), "le bon code au cinquième essai");
        assertEquals("renforce", new JdbcTemplate(proprietaire).queryForObject(
                "SELECT niveau_atteint FROM session_signataire WHERE enveloppe_id = ?::uuid ORDER BY expire_le DESC LIMIT 1", String.class, id));
        assertEquals(409, verifier(jetonSession, code).getStatus(), "à usage unique");

        mvc.perform(MockMvcRequestBuilders.post("/api/v1/signataire/code").header("X-Session-Signataire", jetonSession)
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("canal", canal))));
        for (int i = 0; i < 5; i++) verifier(jetonSession, "999999".equals(code) ? "888888" : "999999");
        MockHttpServletResponse verrouille = verifier(jetonSession, "123456");
        assertEquals(409, verrouille.getStatus());
        assertTrue(corps(verrouille).get("type").asText().endsWith("/code-verrouille"));
    }

    @Test
    void la_demande_de_code_ne_dit_rien_du_canal_et_refuse_un_canal_hors_referentiel() throws Exception {
        String id = envoyee();
        String jetonSession = corps(ouvrir(invitations.pour(id).get(0).get("jeton"))).get("jetonSession").asText();
        assertEquals(202, mvc.perform(MockMvcRequestBuilders.post("/api/v1/signataire/code").header("X-Session-Signataire", jetonSession)
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("canal", valeur("canal-code", "sms")))))
                .andReturn().getResponse().getStatus(), "aucun téléphone : même réponse");
        assertEquals(422, mvc.perform(MockMvcRequestBuilders.post("/api/v1/signataire/code").header("X-Session-Signataire", jetonSession)
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("canal", "pigeon")))).andReturn().getResponse().getStatus());
        assertEquals("code-absent", corps(verifier(corps(ouvrir(invitations.pour(id).get(0).get("jeton"))).get("jetonSession").asText(), "123456"))
                .get("type").asText().replaceAll(".*/", ""));
    }

    private MockHttpServletResponse verifier(String session, String code) throws Exception {
        return mvc.perform(MockMvcRequestBuilders.post("/api/v1/signataire/code/verification").header("X-Session-Signataire", session)
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("code", code)))).andReturn().getResponse();
    }

    private MockHttpServletResponse sessionIntegree(String enveloppe, UUID signataire, List<String> actions, JwtRequestPostProcessor qui) throws Exception {
        return mvc.perform(MockMvcRequestBuilders.post("/api/v1/envelopes/" + enveloppe + "/sessions").with(qui).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("signataire", signataire.toString(), "actions", actions)))).andReturn().getResponse();
    }

    private String ancetres(String jeton) throws Exception {
        MockHttpServletRequestBuilder r = MockMvcRequestBuilders.get("/api/v1/signataire/integration/ancetres");
        if (jeton != null) {
            r = r.header("X-Jeton-Integration", jeton);
        }
        MockHttpServletResponse reponse = mvc.perform(r).andReturn().getResponse();
        assertEquals(204, reponse.getStatus());
        assertEquals("no-store", reponse.getHeader("Cache-Control"));
        return reponse.getHeader("X-Ancetres");
    }

    @Test
    void seule_une_session_integree_peut_etre_affichee_dans_la_page_d_une_application() throws Exception {
        String id = envoyee();
        String invitation = invitations.pour(id).get(0).get("jeton");
        assertEquals("'none'", ancetres(invitation), "lien d'invitation : jamais intégré");
        assertFalse(corps(ouvrir(invitation)).get("originesIntegration").elements().hasNext());
        assertEquals("'none'", ancetres("inconnu"));
        assertEquals("'none'", ancetres(null));
        assertEquals("'none'", ancetres("x".repeat(101)));
        JwtRequestPostProcessor app = jwt().jwt(j -> j.subject(this.app.clientId()).claim("org", this.app.organisation().toString())
                .claim("client_id", this.app.clientId())).authorities(new SimpleGrantedAuthority("SCOPE_sessions-creer"));
        String jeton = corps(sessionIntegree(id, signataire(id, 1), List.of("signer"), app)).get("url").asText().replaceAll(".*/integre/", "");
        assertEquals("'none'", ancetres(jeton), "application sans origine déclarée");
        new JdbcTemplate(proprietaire).update("UPDATE application_cliente SET origines_integration = ARRAY['https://rh.exemple.ca'], statut = 'revoquee', revoque_le = now() WHERE client_id = ?",
                this.app.clientId());
        assertEquals("'none'", ancetres(jeton), "application révoquée");
    }

    private UUID signataire(String enveloppe, int ordre) {
        return UUID.fromString(new JdbcTemplate(proprietaire).queryForObject(
                "SELECT id::text FROM signataire WHERE enveloppe_id = ?::uuid AND ordre = ?", String.class, enveloppe, ordre));
    }

    @Test
    void une_session_integree_sert_une_fois_pendant_cinq_minutes_pour_les_actions_demandees() throws Exception {
        String id = envoyee();
        JwtRequestPostProcessor app = jwt().jwt(j -> j.subject(this.app.clientId()).claim("org", this.app.organisation().toString())
                .claim("client_id", this.app.clientId())).authorities(new SimpleGrantedAuthority("SCOPE_sessions-creer"));
        MockHttpServletResponse r = sessionIntegree(id, signataire(id, 1), List.of(valeur("action-signataire", "signer")), app);
        assertEquals(201, r.getStatus(), r.getContentAsString());
        JsonNode integration = corps(r);
        assertTrue(Instant.parse(integration.get("expireLe").asText()).isBefore(Instant.now().plusSeconds(301)), "5 minutes");
        String jetonIntegre = integration.get("url").asText().replaceAll(".*/integre/", "");
        new JdbcTemplate(proprietaire).update("UPDATE application_cliente SET origines_integration = ARRAY['https://rh.exemple.ca', 'https://a.exemple.ca'] WHERE client_id = ?",
                this.app.clientId());
        assertEquals("https://a.exemple.ca https://rh.exemple.ca", ancetres(jetonIntegre), "origines déclarées, triées, avant l'ouverture");
        JsonNode session = corps(ouvrir(jetonIntegre));
        assertEquals(List.of("signer"), List.of(session.get("actions").get(0).asText()));
        assertEquals("[\"https://a.exemple.ca\",\"https://rh.exemple.ca\"]", session.get("originesIntegration").toString());
        assertEquals(404, ouvrir(jetonIntegre).getStatus(), "réutilisée : refusée");
        new JdbcTemplate(proprietaire).update("UPDATE lien_signature SET expire_le = now() WHERE utilisations_max = 1 AND enveloppe_id = ?::uuid", id);
        String autre = corps(sessionIntegree(id, signataire(id, 1), List.of("signer"), app)).get("url").asText().replaceAll(".*/integre/", "");
        new JdbcTemplate(proprietaire).update("UPDATE lien_signature SET expire_le = now() - interval '1 second' WHERE utilisations = 0 AND utilisations_max = 1 AND enveloppe_id = ?::uuid", id);
        assertEquals(404, ouvrir(autre).getStatus(), "expirée : refusée");
    }

    @Test
    void une_session_integree_n_est_ouverte_que_pour_son_enveloppe_son_tour_et_des_actions_connues() throws Exception {
        String id = envoyee();
        JwtRequestPostProcessor app = jwt().jwt(j -> j.subject(this.app.clientId()).claim("org", this.app.organisation().toString())
                .claim("client_id", this.app.clientId())).authorities(new SimpleGrantedAuthority("SCOPE_sessions-creer"));
        assertEquals(409, sessionIntegree(id, signataire(id, 2), List.of("signer"), app).getStatus(), "pas encore son tour");
        assertEquals(422, sessionIntegree(id, signataire(id, 1), List.of("tout-faire"), app).getStatus());
        assertEquals(400, sessionIntegree(id, signataire(id, 1), List.of(), app).getStatus());
        ApplicationsDeTest.Application voisine = applications.enregistrer(this.app.organisation(), "sessions-creer");
        JwtRequestPostProcessor autre = jwt().jwt(j -> j.subject(voisine.clientId()).claim("org", voisine.organisation().toString())
                .claim("client_id", voisine.clientId())).authorities(new SimpleGrantedAuthority("SCOPE_sessions-creer"));
        assertEquals(404, sessionIntegree(id, signataire(id, 1), List.of("signer"), autre).getStatus(), "enveloppe d'une autre application");
        assertEquals(403, sessionIntegree(id, signataire(id, 1), List.of("signer"), jeton()).getStatus(), "portée requise");
    }

    @Test
    void le_code_par_sms_arrive_au_telephone_chiffre_du_signataire() throws Exception {
        String corpsEnveloppe = json.writeValueAsString(Map.of("externalReference", "SMS-" + UUID.randomUUID(),
                "mode", valeur("mode-enveloppe", "empreinte"), "expiresAt", Instant.now().plusSeconds(86400).toString()));
        String id = corps(mvc.perform(MockMvcRequestBuilders.post("/api/v1/envelopes").with(jeton()).header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON).content(corpsEnveloppe)).andReturn().getResponse()).get("id").asText();
        String numero = "+1418555" + String.format("%04d", (int) (Math.random() * 10000));
        mvc.perform(MockMvcRequestBuilders.put("/api/v1/envelopes/" + id + "/signers").with(jeton()).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(List.of(Map.of("nom", "Carole", "courriel", "carole@exemple.ca", "telephone", numero,
                        "langue", valeur("langue", "fr"), "type", valeur("type-signataire", "invite"), "ordre", 1)))));
        mvc.perform(MockMvcRequestBuilders.post("/api/v1/envelopes/" + id + "/send").with(jeton()));
        assertEquals(0, new JdbcTemplate(proprietaire).queryForObject(
                "SELECT count(*) FROM signataire WHERE enveloppe_id = ?::uuid AND position(convert_to(?, 'UTF8') IN telephone_chiffre) > 0", Integer.class, id,
                numero.substring(1)), "le téléphone n'est jamais conservé en clair (comparaison d'octets : le chiffré peut contenir un octet nul)");
        String session = corps(ouvrir(invitations.pour(id).get(0).get("jeton"))).get("jetonSession").asText();
        assertEquals(202, mvc.perform(MockMvcRequestBuilders.post("/api/v1/signataire/code").header("X-Session-Signataire", session)
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("canal", valeur("canal-code", "sms")))))
                .andReturn().getResponse().getStatus());
        assertTrue(expedierSms.executer() >= 1);
        String texte = new JdbcTemplate(proprietaire).queryForObject("SELECT texte FROM sms_simule WHERE numero = ? ORDER BY id DESC LIMIT 1",
                String.class, numero);
        String code = texte.replaceAll("(?s).*?(\\d{6}).*", "$1");
        assertTrue(corps(verifier(session, code)).get("valide").asBoolean());
    }

    @Test
    void une_relance_reemet_le_lien_apres_la_frequence_et_s_arrete_au_maximum() throws Exception {
        String id = envoyee();
        String premier = invitations.pour(id).get(0).get("jeton");
        JdbcTemplate proprio = new JdbcTemplate(proprietaire);
        proprio.update("UPDATE lien_signature SET emis_le = now() - interval '3 days' WHERE enveloppe_id = ?::uuid", id);
        assertTrue(relances.executer() >= 1);
        List<Map<String, String>> recues = invitations.pour(id);
        Map<String, String> relance = recues.get(recues.size() - 1);
        assertEquals("relance", relance.get("modele"));
        assertEquals(404, ouvrir(premier).getStatus(), "l'ancien lien ne fonctionne plus");
        assertEquals(200, ouvrir(relance.get("jeton")).getStatus());
        assertEquals(1, proprio.queryForObject("SELECT rang_relance FROM lien_signature WHERE enveloppe_id = ?::uuid AND revoque_le IS NULL",
                Integer.class, id));
        int avant = invitations.pour(id).size();
        relances.executer();
        assertEquals(avant, invitations.pour(id).size(), "fréquence non écoulée : rien");
        proprio.update("UPDATE lien_signature SET emis_le = now() - interval '3 days', rang_relance = 3 WHERE enveloppe_id = ?::uuid AND revoque_le IS NULL", id);
        relances.executer();
        assertEquals(avant, invitations.pour(id).size(), "maximum atteint : plus de relance");
    }
}
