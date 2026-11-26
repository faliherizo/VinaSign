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
import com.webauthn4j.data.AttestationConveyancePreference;
import com.webauthn4j.data.AuthenticatorAssertionResponse;
import com.webauthn4j.data.AuthenticatorAttestationResponse;
import com.webauthn4j.data.AuthenticatorSelectionCriteria;
import com.webauthn4j.data.PublicKeyCredential;
import com.webauthn4j.data.PublicKeyCredentialCreationOptions;
import com.webauthn4j.data.PublicKeyCredentialDescriptor;
import com.webauthn4j.data.PublicKeyCredentialParameters;
import com.webauthn4j.data.PublicKeyCredentialRequestOptions;
import com.webauthn4j.data.PublicKeyCredentialRpEntity;
import com.webauthn4j.data.PublicKeyCredentialType;
import com.webauthn4j.data.PublicKeyCredentialUserEntity;
import com.webauthn4j.data.ResidentKeyRequirement;
import com.webauthn4j.data.UserVerificationRequirement;
import com.webauthn4j.data.attestation.statement.COSEAlgorithmIdentifier;
import com.webauthn4j.data.client.Origin;
import com.webauthn4j.data.client.challenge.DefaultChallenge;
import com.webauthn4j.test.EmulatorUtil;
import com.webauthn4j.test.authenticator.webauthn.WebAuthnAuthenticatorAdaptor;
import com.webauthn4j.test.client.ClientPlatform;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
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
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/** VS-043 — un signataire récurrent enregistre une clé d'accès, puis s'authentifie avec elle d'une enveloppe à l'autre. */
@SpringBootTest
@AutoConfigureMockMvc
@Import({PostgresDeTest.class, ApplicationsDeTest.class, ReferentielsSql.class, InvitationsCapturees.class})
@TestPropertySource(properties = {"vinasign.session.cles-acces.rp-id=signer.vinasign.test", "vinasign.session.cles-acces.origine=https://signer.vinasign.test"})
class CleAccesApiTest {

    private static final List<String> PORTEES = List.of("enveloppes-creer", "enveloppes-envoyer", "signataires-gerer");
    private static final Base64.Encoder B64 = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder D64 = Base64.getUrlDecoder();

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired ApplicationsDeTest applications;
    @Autowired InvitationsCapturees invitations;
    @Autowired @LiquibaseDataSource DataSource proprietaire;

    private final ClientPlatform navigateur = new ClientPlatform(new Origin("https://signer.vinasign.test"),
            new WebAuthnAuthenticatorAdaptor(EmulatorUtil.PACKED_AUTHENTICATOR));

    /** Enveloppe envoyée à cette personne ; rend la session ouverte par son lien. */
    private String session(Application app, String courriel) throws Exception {
        var jeton = jwt().jwt(j -> j.subject(app.clientId()).claim("org", app.organisation().toString()).claim("client_id", app.clientId()))
                .authorities(PORTEES.stream().<GrantedAuthority>map(p -> new SimpleGrantedAuthority("SCOPE_" + p)).toList());
        String id = json.readTree(mvc.perform(MockMvcRequestBuilders.post("/api/v1/envelopes").with(jeton).header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("externalReference", "CA-" + UUID.randomUUID(), "mode", "empreinte",
                        "expiresAt", Instant.now().plusSeconds(86400).toString())))).andReturn().getResponse().getContentAsString()).get("id").asText();
        mvc.perform(MockMvcRequestBuilders.put("/api/v1/envelopes/" + id + "/signers").with(jeton).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(List.of(Map.of("nom", "Gabrielle", "courriel", courriel, "langue", "fr", "type", "invite", "ordre", 1,
                        "niveau", "renforce")))));
        mvc.perform(MockMvcRequestBuilders.post("/api/v1/envelopes/" + id + "/send").with(jeton));
        String lien = invitations.pour(id).get(0).get("jeton");
        JsonNode vue = json.readTree(mvc.perform(MockMvcRequestBuilders.post("/api/v1/signataire/ouverture").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("jeton", lien)))).andReturn().getResponse().getContentAsString());
        assertEquals("renforce", vue.get("niveauExige").asText(), "l'interface sait quel niveau atteindre");
        String session = vue.get("jetonSession").asText();
        mvc.perform(MockMvcRequestBuilders.post("/api/v1/signataire/consentement").header("X-Session-Signataire", session)
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("version", vue.get("versionConsentement").asText()))));
        return session;
    }

    private MockHttpServletResponse post(String session, String chemin, Object corps) throws Exception {
        var r = MockMvcRequestBuilders.post("/api/v1/signataire/cles-acces/" + chemin).header("X-Session-Signataire", session);
        if (corps != null) r = r.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(corps));
        return mvc.perform(r).andReturn().getResponse();
    }

    private JsonNode corps(MockHttpServletResponse r) throws Exception {
        return json.readTree(r.getContentAsString(StandardCharsets.UTF_8));
    }

    private static String empreinte(String jeton) throws Exception {
        return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(jeton.getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void la_cle_s_enregistre_apres_un_code_puis_authentifie_la_meme_personne_sur_une_autre_enveloppe() throws Exception {
        Application app = applications.enregistrer(PORTEES.toArray(String[]::new));
        String courriel = "gabrielle-" + UUID.randomUUID() + "@exemple.ca";
        String premiere = session(app, courriel);
        assertTrue(corps(post(premiere, "enregistrement/options", null)).get("type").asText().endsWith("authentification-prealable-requise"));
        JdbcTemplate base = new JdbcTemplate(proprietaire);
        base.update("UPDATE session_signataire SET niveau_atteint = 'renforce' WHERE empreinte_jeton = ?", empreinte(premiere));

        JsonNode o = corps(post(premiere, "enregistrement/options", null));
        assertEquals("signer.vinasign.test", o.get("rpId").asText());
        assertEquals("Gabrielle", o.get("nomAffiche").asText());
        PublicKeyCredential<AuthenticatorAttestationResponse, ?> cree = navigateur.create(new PublicKeyCredentialCreationOptions(
                new PublicKeyCredentialRpEntity(o.get("rpId").asText(), "VinaSign"),
                new PublicKeyCredentialUserEntity(D64.decode(o.get("identifiantUtilisateur").asText()), "signataire", o.get("nomAffiche").asText()),
                new DefaultChallenge(D64.decode(o.get("defi").asText())),
                List.of(new PublicKeyCredentialParameters(PublicKeyCredentialType.PUBLIC_KEY, COSEAlgorithmIdentifier.ES256)), o.get("delaiMs").asLong(),
                List.of(), new AuthenticatorSelectionCriteria(null, ResidentKeyRequirement.PREFERRED, UserVerificationRequirement.REQUIRED),
                AttestationConveyancePreference.NONE, null));
        MockHttpServletResponse enregistree = post(premiere, "enregistrement", Map.of("attestationObject", B64.encodeToString(cree.getResponse().getAttestationObject()),
                "clientDataJSON", B64.encodeToString(cree.getResponse().getClientDataJSON())));
        assertEquals(204, enregistree.getStatus(), enregistree.getContentAsString());
        assertEquals(1, base.queryForObject("SELECT count(*) FROM cle_acces WHERE credential_id = ?", Integer.class, B64.encodeToString(cree.getRawId())));

        String seconde = session(app, courriel.toUpperCase());
        JsonNode a = corps(post(seconde, "authentification/options", null));
        assertEquals(List.of(B64.encodeToString(cree.getRawId())), List.of(a.get("clesPermises").get(0).asText()), "même personne, autre enveloppe");
        List<PublicKeyCredentialDescriptor> permises = new ArrayList<>();
        a.get("clesPermises").forEach(c -> permises.add(new PublicKeyCredentialDescriptor(PublicKeyCredentialType.PUBLIC_KEY, D64.decode(c.asText()), null)));
        PublicKeyCredential<AuthenticatorAssertionResponse, ?> assertion = navigateur.get(new PublicKeyCredentialRequestOptions(
                new DefaultChallenge(D64.decode(a.get("defi").asText())), a.get("delaiMs").asLong(), a.get("rpId").asText(), permises,
                UserVerificationRequirement.REQUIRED, null));
        Map<String, String> corpsAssertion = Map.of("credentialId", B64.encodeToString(assertion.getRawId()),
                "authenticatorData", B64.encodeToString(assertion.getResponse().getAuthenticatorData()),
                "clientDataJSON", B64.encodeToString(assertion.getResponse().getClientDataJSON()),
                "signature", B64.encodeToString(assertion.getResponse().getSignature()));
        MockHttpServletResponse avant = mvc.perform(MockMvcRequestBuilders.post("/api/v1/signataire/signature").header("X-Session-Signataire", seconde)
                .contentType(MediaType.APPLICATION_JSON).content("{\"nomSaisi\":\"Gabrielle\"}")).andReturn().getResponse();
        assertEquals(403, avant.getStatus());
        assertTrue(corps(avant).get("type").asText().endsWith("authentification-requise"), "niveau renforcé exigé avant de signer");
        MockHttpServletResponse authentifiee = post(seconde, "authentification", corpsAssertion);
        assertEquals(204, authentifiee.getStatus(), authentifiee.getContentAsString());
        assertEquals(Map.of("niveau_atteint", "renforce", "amr", "hwk"),
                base.queryForMap("SELECT niveau_atteint, amr FROM session_signataire WHERE empreinte_jeton = ?", empreinte(seconde)));
        assertTrue(corps(post(seconde, "authentification", corpsAssertion)).get("type").asText().endsWith("cle-acces-refusee"), "défi à usage unique");
        assertEquals(204, mvc.perform(MockMvcRequestBuilders.post("/api/v1/signataire/signature").header("X-Session-Signataire", seconde)
                .contentType(MediaType.APPLICATION_JSON).content("{\"nomSaisi\":\"Gabrielle\"}")).andReturn().getResponse().getStatus(),
                "niveau atteint par la clé d'accès : la signature passe");
    }

    @Test
    void sans_cle_le_signataire_se_rabat_sur_le_code_et_une_reponse_falsifiee_est_refusee() throws Exception {
        Application app = applications.enregistrer(PORTEES.toArray(String[]::new));
        String s = session(app, "sans-cle-" + UUID.randomUUID() + "@exemple.ca");
        MockHttpServletResponse r = post(s, "authentification/options", null);
        assertEquals(404, r.getStatus());
        assertTrue(corps(r).get("type").asText().endsWith("aucune-cle-acces"));
        new JdbcTemplate(proprietaire).update("UPDATE session_signataire SET niveau_atteint = 'renforce' WHERE empreinte_jeton = ?", empreinte(s));
        post(s, "enregistrement/options", null);
        MockHttpServletResponse faux = post(s, "enregistrement", Map.of("attestationObject", B64.encodeToString("faux".getBytes()),
                "clientDataJSON", B64.encodeToString("{}".getBytes())));
        assertEquals(403, faux.getStatus());
        assertTrue(corps(post(s, "enregistrement", Map.of("attestationObject", "AA", "clientDataJSON", "AA"))).get("type").asText()
                .endsWith("cle-acces-refusee"), "sans défi en cours");
        assertEquals(401, mvc.perform(MockMvcRequestBuilders.post("/api/v1/signataire/cles-acces/authentification/options")).andReturn().getResponse()
                .getStatus(), "session de signataire exigée");
    }
}
