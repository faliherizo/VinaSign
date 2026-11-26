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
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/**
 * VS-044 — l'application atteste son utilisateur par une assertion signée de sa clé : sujet du signataire, seconde preuve
 * récente au niveau renforcé, usage unique ; la session ouverte part du niveau attesté.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({PostgresDeTest.class, ApplicationsDeTest.class, ReferentielsSql.class, InvitationsCapturees.class})
class AttestationApiTest {

    private static final List<String> PORTEES = List.of("enveloppes-creer", "enveloppes-lire", "enveloppes-envoyer", "signataires-gerer", "sessions-creer");
    private static final String EMETTEUR = "https://auth.vinasign.test";

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired ApplicationsDeTest applications;
    @Autowired @LiquibaseDataSource DataSource proprietaire;

    private Application app;
    private String sujet;

    @BeforeEach
    void preparer() {
        app = applications.enregistrer(PORTEES.toArray(String[]::new));
        sujet = "medecin-" + UUID.randomUUID();
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
            if (v.get("code").asText().equals(code)) return code;
        }
        throw new IllegalStateException(referentiel + "/" + code);
    }

    /** Enveloppe envoyée à un utilisateur de l'application ; rend [enveloppe, signataire]. */
    private String[] envoyee() throws Exception {
        String id = corps(mvc.perform(MockMvcRequestBuilders.post("/api/v1/envelopes").with(jeton()).header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("externalReference", "A-" + UUID.randomUUID(),
                        "mode", valeur("mode-enveloppe", "empreinte"), "expiresAt", Instant.now().plusSeconds(86400).toString()))))
                .andReturn().getResponse()).get("id").asText();
        String signataire = corps(mvc.perform(MockMvcRequestBuilders.put("/api/v1/envelopes/" + id + "/signers").with(jeton())
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(List.of(Map.of("nom", "Dre Roy", "langue", valeur("langue", "fr"),
                        "type", valeur("type-signataire", "utilisateur-application"), "ordre", 1, "sujetExterne", sujet)))))
                .andReturn().getResponse()).get("signataires").get(0).get("id").asText();
        assertEquals(200, mvc.perform(MockMvcRequestBuilders.post("/api/v1/envelopes/" + id + "/send").with(jeton())).andReturn().getResponse().getStatus());
        return new String[] {id, signataire};
    }

    /** Assertion signée de la clé de l'application ; chaque ajustement modifie les revendications par défaut. */
    private String attestation(RSAKey cle, Consumer<JWTClaimsSet.Builder> ajustement) throws Exception {
        Instant t = Instant.now();
        JWTClaimsSet.Builder b = new JWTClaimsSet.Builder().issuer(app.clientId()).subject(sujet).audience(EMETTEUR + "/attestations")
                .jwtID(UUID.randomUUID().toString()).issueTime(Date.from(t)).expirationTime(Date.from(t.plusSeconds(60)))
                .claim("amr", List.of("pwd", "otp")).claim("auth_time", t.minusSeconds(60).getEpochSecond());
        ajustement.accept(b);
        SignedJWT jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(cle.getKeyID()).build(), b.build());
        jwt.sign(new RSASSASigner(cle));
        return jwt.serialize();
    }

    private MockHttpServletResponse demander(String[] e, String attestation, String niveau) throws Exception {
        Map<String, Object> demande = new HashMap<>(Map.of("signataire", e[1], "actions", List.of(valeur("action-signataire", "signer"))));
        if (attestation != null) demande.put("attestation", attestation);
        if (niveau != null) demande.put("niveau", niveau);
        return mvc.perform(MockMvcRequestBuilders.post("/api/v1/envelopes/" + e[0] + "/sessions").with(jeton()).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(demande))).andReturn().getResponse();
    }

    private void refusee(MockHttpServletResponse r, String motif) throws Exception {
        assertEquals(403, r.getStatus(), motif + " : " + r.getContentAsString());
        assertTrue(corps(r).get("type").asText().endsWith("attestation-refusee"), motif);
    }

    @Test
    void une_attestation_valide_ouvre_une_session_au_niveau_atteste_et_ne_sert_qu_une_fois() throws Exception {
        String[] e = envoyee();
        String renforce = valeur("niveau-authentification", "renforce");
        String forte = attestation(app.cle(), b -> { });
        MockHttpServletResponse r = demander(e, forte, renforce);
        assertEquals(201, r.getStatus(), r.getContentAsString());
        String jeton = corps(r).get("url").asText().replaceAll(".*/integre/", "");
        JdbcTemplate base = new JdbcTemplate(proprietaire);
        assertEquals(List.of(Map.of("sujet", sujet, "niveau", renforce, "methodes", "{pwd,otp}")), base.queryForList(
                "SELECT a.sujet, a.niveau, a.methodes::text AS methodes FROM attestation_lien a JOIN lien_signature l ON l.id = a.lien_id WHERE l.enveloppe_id = ?::uuid", e[0]));
        assertEquals(200, mvc.perform(MockMvcRequestBuilders.post("/api/v1/signataire/ouverture").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("jeton", jeton)))).andReturn().getResponse().getStatus());
        assertEquals(renforce, base.queryForObject("SELECT niveau_atteint FROM session_signataire WHERE enveloppe_id = ?::uuid", String.class, e[0]),
                "la session part du niveau attesté");
        refusee(demander(e, forte, renforce), "rejouée");
        assertEquals(201, demander(e, attestation(app.cle(), b -> b.claim("amr", List.of("pwd"))), null).getStatus(), "niveau par défaut : mot de passe suffit");
    }

    @Test
    void sans_attestation_valide_pour_ce_signataire_la_session_est_refusee() throws Exception {
        String[] e = envoyee();
        String renforce = valeur("niveau-authentification", "renforce");
        RSAKey etrangere = new RSAKeyGenerator(2048).keyID("etrangere").generate();
        refusee(demander(e, null, null), "absente");
        refusee(demander(e, "pas.un.jwt", null), "illisible");
        refusee(demander(e, attestation(etrangere, b -> { }), null), "signée d'une clé non enregistrée");
        refusee(demander(e, attestation(app.cle(), b -> b.subject("quelqu-un-d-autre")), null), "autre sujet");
        refusee(demander(e, attestation(app.cle(), b -> b.issuer("autre-application")), null), "autre émetteur");
        refusee(demander(e, attestation(app.cle(), b -> b.audience(EMETTEUR)), null), "assertion d'authentification du client réutilisée");
        refusee(demander(e, attestation(app.cle(), b -> b.expirationTime(Date.from(Instant.now().minusSeconds(120)))), null), "expirée");
        refusee(demander(e, attestation(app.cle(), b -> b.expirationTime(Date.from(Instant.now().plusSeconds(3600)))), null), "trop longue");
        refusee(demander(e, attestation(app.cle(), b -> b.jwtID(null)), null), "sans jti");
        refusee(demander(e, attestation(app.cle(), b -> b.claim("amr", "otp")), null), "amr qui n'est pas une liste");
        refusee(demander(e, attestation(app.cle(), b -> b.claim("auth_time", "hier")), null), "auth_time illisible");
        refusee(demander(e, attestation(app.cle(), b -> b.claim("amr", List.of("pwd"))), renforce), "renforcé sans seconde preuve");
        refusee(demander(e, attestation(app.cle(), b -> b.claim("auth_time", Instant.now().minusSeconds(600).getEpochSecond())), renforce),
                "renforcé, preuve trop ancienne");
        new JdbcTemplate(proprietaire).update("UPDATE application_cliente SET statut = 'revoquee', revoque_le = now() WHERE client_id = ?", app.clientId());
        assertEquals(403, demander(e, attestation(app.cle(), b -> { }), null).getStatus(), "application révoquée : refusée avant même l'attestation");
    }
}
