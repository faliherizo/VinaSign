package ca.vinaitsolution.vinasign.session;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

import ca.vinaitsolution.vinasign.commun.application.port.ExecutionOrganisation;
import ca.vinaitsolution.vinasign.commun.application.port.LecturePreuvesSignataires;
import ca.vinaitsolution.vinasign.commun.domain.IdentifiantOrganisation;
import ca.vinaitsolution.vinasign.support.ApplicationsDeTest;
import ca.vinaitsolution.vinasign.support.ApplicationsDeTest.Application;
import ca.vinaitsolution.vinasign.support.InvitationsCapturees;
import ca.vinaitsolution.vinasign.support.PostgresDeTest;
import ca.vinaitsolution.vinasign.support.ReferentielsSql;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/**
 * VS-131 — l'organisation exige une identité vérifiée : le signataire est envoyé chez le fournisseur ; son rappel signé
 * élève la session ; la signature ne passe qu'ensuite ; la preuve consigne fournisseur, méthode et assurance.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({PostgresDeTest.class, ApplicationsDeTest.class, ReferentielsSql.class, InvitationsCapturees.class})
@TestPropertySource(properties = {"vinasign.session.verification-identite.fournisseur=generique",
        "vinasign.session.verification-identite.url=https://idv.exemple.test/verifier?ref={reference}&lang={langue}",
        "vinasign.session.verification-identite.secret=" + VerificationIdentiteApiTest.SECRET})
class VerificationIdentiteApiTest {

    static final String SECRET = "secret-partage-de-test-de-32-caracteres-au-moins";
    private static final List<String> PORTEES = List.of("enveloppes-creer", "enveloppes-envoyer", "signataires-gerer");

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired ApplicationsDeTest applications;
    @Autowired InvitationsCapturees invitations;
    @Autowired LecturePreuvesSignataires preuves;
    @Autowired ExecutionOrganisation execution;

    /** Le niveau exigé est découvert dans le référentiel : celui qui demande une vérification d'identité. */
    private String niveauIdentite() throws Exception {
        for (JsonNode v : json.readTree(mvc.perform(MockMvcRequestBuilders.get("/api/v1/referentiels/niveau-authentification")).andReturn().getResponse()
                .getContentAsString()).get("valeurs")) {
            if ("verification-identite".equals(v.path("attributs").path("methode").asText())) return v.get("code").asText();
        }
        throw new IllegalStateException("aucun niveau à vérification d'identité");
    }

    /** Rend [enveloppe, signataire, session]. */
    private String[] session(Application app) throws Exception {
        var jeton = jwt().jwt(j -> j.subject(app.clientId()).claim("org", app.organisation().toString()).claim("client_id", app.clientId()))
                .authorities(PORTEES.stream().<GrantedAuthority>map(p -> new SimpleGrantedAuthority("SCOPE_" + p)).toList());
        String id = json.readTree(mvc.perform(MockMvcRequestBuilders.post("/api/v1/envelopes").with(jeton).header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("externalReference", "IDV-" + UUID.randomUUID(), "mode", "empreinte",
                        "expiresAt", Instant.now().plusSeconds(86400).toString())))).andReturn().getResponse().getContentAsString()).get("id").asText();
        String signataire = json.readTree(mvc.perform(MockMvcRequestBuilders.put("/api/v1/envelopes/" + id + "/signers").with(jeton).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(List.of(Map.of("nom", "Héloïse", "courriel", "heloise@exemple.ca", "langue", "fr", "type", "invite", "ordre", 1,
                        "niveau", niveauIdentite()))))).andReturn().getResponse().getContentAsString()).get("signataires").get(0).get("id").asText();
        mvc.perform(MockMvcRequestBuilders.post("/api/v1/envelopes/" + id + "/send").with(jeton));
        JsonNode vue = json.readTree(mvc.perform(MockMvcRequestBuilders.post("/api/v1/signataire/ouverture").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("jeton", invitations.pour(id).get(0).get("jeton"))))).andReturn().getResponse().getContentAsString());
        String session = vue.get("jetonSession").asText();
        mvc.perform(MockMvcRequestBuilders.post("/api/v1/signataire/consentement").header("X-Session-Signataire", session)
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("version", vue.get("versionConsentement").asText()))));
        return new String[] {id, signataire, session};
    }

    private MockHttpServletResponse signer(String session) throws Exception {
        return mvc.perform(MockMvcRequestBuilders.post("/api/v1/signataire/signature").header("X-Session-Signataire", session)
                .contentType(MediaType.APPLICATION_JSON).content("{\"nomSaisi\":\"Héloïse\"}")).andReturn().getResponse();
    }

    private static String signature(byte[] corps, String secret) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return "sha256=" + HexFormat.of().formatHex(mac.doFinal(corps));
    }

    private MockHttpServletResponse rappel(Map<String, String> resultat, String secret) throws Exception {
        byte[] corps = json.writeValueAsBytes(resultat);
        return mvc.perform(MockMvcRequestBuilders.post("/api/v1/verification-identite/rappel").contentType(MediaType.APPLICATION_JSON)
                .header("X-VinaSign-Signature", signature(corps, secret)).content(corps)).andReturn().getResponse();
    }

    private static String reference(String url) {
        String requete = URI.create(url).getRawQuery();
        return java.net.URLDecoder.decode(requete.replaceAll(".*ref=([^&]*).*", "$1"), StandardCharsets.UTF_8);
    }

    @Test
    void le_rappel_signe_du_fournisseur_eleve_la_session_et_la_preuve_le_consigne() throws Exception {
        Application app = applications.enregistrer(PORTEES.toArray(String[]::new));
        String[] s = session(app);
        MockHttpServletResponse avant = signer(s[2]);
        assertEquals(403, avant.getStatus(), "identité non vérifiée : pas de signature");

        JsonNode demarrage = json.readTree(mvc.perform(MockMvcRequestBuilders.post("/api/v1/signataire/verification-identite").header("X-Session-Signataire", s[2]))
                .andReturn().getResponse().getContentAsString());
        String url = demarrage.get("url").asText();
        assertTrue(url.startsWith("https://idv.exemple.test/verifier?ref=") && url.endsWith("&lang=fr"), url);
        String ref = reference(url);
        String suivi = "/api/v1/signataire/verification-identite/" + demarrage.get("verification").asText();
        assertEquals("en-cours", json.readTree(mvc.perform(MockMvcRequestBuilders.get(suivi).header("X-Session-Signataire", s[2])).andReturn().getResponse()
                .getContentAsString()).get("etat").asText());

        assertEquals(403, rappel(Map.of("reference", ref, "resultat", "reussie"), "autre-secret-de-trente-deux-caracteres-au-moins").getStatus(),
                "rappel non signé par le fournisseur");
        assertEquals(403, rappel(Map.of("reference", "inconnue", "resultat", "reussie"), SECRET).getStatus(), "référence inconnue");
        Map<String, String> reussie = Map.of("reference", ref, "resultat", "reussie", "methode", "piece-identite", "assurance", "substantiel");
        assertEquals(204, rappel(reussie, SECRET).getStatus());
        assertEquals(204, rappel(reussie, SECRET).getStatus(), "rappel répété : sans effet");
        assertEquals(403, rappel(Map.of("reference", ref, "resultat", "echouee"), SECRET).getStatus(), "résultat contraire refusé");
        assertEquals("reussie", json.readTree(mvc.perform(MockMvcRequestBuilders.get(suivi).header("X-Session-Signataire", s[2])).andReturn().getResponse()
                .getContentAsString()).get("etat").asText());

        assertEquals(204, signer(s[2]).getStatus(), "identité vérifiée : la signature passe");
        List<String> methodes = execution.pour(new IdentifiantOrganisation(app.organisation()), "plateforme:essai",
                () -> preuves.de(UUID.fromString(s[0])).get(0).methodesAttestees());
        assertEquals(List.of("idv", "fournisseur:generique", "methode:piece-identite", "assurance:substantiel"), methodes);
    }

    @Test
    void une_verification_echouee_ne_donne_rien_et_reste_propre_a_sa_session() throws Exception {
        Application app = applications.enregistrer(PORTEES.toArray(String[]::new));
        String[] s = session(app);
        JsonNode demarrage = json.readTree(mvc.perform(MockMvcRequestBuilders.post("/api/v1/signataire/verification-identite").header("X-Session-Signataire", s[2]))
                .andReturn().getResponse().getContentAsString());
        assertEquals(204, rappel(Map.of("reference", reference(demarrage.get("url").asText()), "resultat", "echouee"), SECRET).getStatus());
        assertEquals(403, signer(s[2]).getStatus(), "échec : niveau non atteint");
        String[] autre = session(app);
        assertEquals(404, mvc.perform(MockMvcRequestBuilders.get("/api/v1/signataire/verification-identite/" + demarrage.get("verification").asText())
                .header("X-Session-Signataire", autre[2])).andReturn().getResponse().getStatus(), "vérification d'une autre session");
        assertEquals(401, mvc.perform(MockMvcRequestBuilders.post("/api/v1/signataire/verification-identite")).andReturn().getResponse().getStatus());
    }
}
