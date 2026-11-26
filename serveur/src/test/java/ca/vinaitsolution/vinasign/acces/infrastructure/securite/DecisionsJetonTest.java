package ca.vinaitsolution.vinasign.acces.infrastructure.securite;

import static org.junit.jupiter.api.Assertions.*;

import ca.vinaitsolution.vinasign.acces.application.port.AssertionsConsommees;
import ca.vinaitsolution.vinasign.acces.domain.IdentifiantClient;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings;
import org.springframework.security.oauth2.server.authorization.token.JwtEncodingContext;

/** Décisions des contrôles de jeton, éprouvées une à une hors de la pile HTTP. */
class DecisionsJetonTest {

    private static final Instant MAINTENANT = Instant.parse("2026-11-22T15:00:00Z");
    private static final String CLIENT = "essai-12345678";
    private static final JetonsProprietes REGLAGES = new JetonsProprietes("https://auth.essai", "https://api.essai",
            Duration.ofMinutes(5), Duration.ofMinutes(5), null, true);

    private final Set<String> consommes = new HashSet<>();
    private final AssertionsConsommees registre = new AssertionsConsommees() {
        @Override public boolean consommer(String jti, IdentifiantClient client, Instant expireLe) { return consommes.add(jti); }
        @Override public int purger(Instant maintenant) { return 0; }
    };
    private final DecodeurAssertionsClient decodeur = new DecodeurAssertionsClient(registre, REGLAGES, Clock.fixed(MAINTENANT, ZoneOffset.UTC));

    private static Jwt assertion(String jti, Instant emise, Instant expire) {
        Jwt.Builder b = Jwt.withTokenValue("a").header("alg", "RS256").claim("iss", CLIENT).subject(CLIENT).audience(List.of(REGLAGES.emetteur()));
        if (jti != null) b.jti(jti);
        if (emise != null) b.issuedAt(emise);
        if (expire != null) b.expiresAt(expire);
        return b.build();
    }

    @Test
    void un_jti_vide_ou_demesure_est_refuse_sans_etre_consomme() {
        assertTrue(decodeur.valider(assertion(" ", MAINTENANT, MAINTENANT.plusSeconds(60)), CLIENT).hasErrors());
        assertTrue(decodeur.valider(assertion("x".repeat(201), MAINTENANT, MAINTENANT.plusSeconds(60)), CLIENT).hasErrors());
        assertTrue(consommes.isEmpty());
    }

    @Test
    void sans_iat_l_assertion_est_datee_de_maintenant_et_un_iat_futur_est_refuse() {
        assertFalse(decodeur.valider(assertion("a1", null, MAINTENANT.plusSeconds(60)), CLIENT).hasErrors());
        assertTrue(decodeur.valider(assertion("a2", MAINTENANT.plusSeconds(120), MAINTENANT.plusSeconds(180)), CLIENT).hasErrors());
        assertTrue(decodeur.valider(assertion("a3", MAINTENANT, null), CLIENT).hasErrors(), "sans expiration : refusée");
    }

    @Test
    void des_cles_publiques_illisibles_rendent_un_refus_et_non_une_erreur_interne() {
        RegisteredClient client = RegisteredClient.withId(CLIENT).clientId(CLIENT)
                .clientAuthenticationMethod(ClientAuthenticationMethod.PRIVATE_KEY_JWT)
                .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
                .clientSettings(ClientSettings.builder().setting(ClientsEnregistres.CLES_PUBLIQUES, "{pas du json").build()).build();
        assertThrows(BadJwtException.class, () -> decodeur.createDecoder(client).decode("x.y.z"));
    }

    @Test
    void seul_le_jeton_d_acces_recoit_audience_et_organisation() {
        RegisteredClient client = RegisteredClient.withId(CLIENT).clientId(CLIENT)
                .clientAuthenticationMethod(ClientAuthenticationMethod.PRIVATE_KEY_JWT)
                .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
                .clientSettings(ClientSettings.builder().setting(ClientsEnregistres.ORGANISATION, "org").build()).build();
        JwtClaimsSet.Builder revendications = JwtClaimsSet.builder().subject(CLIENT);
        new PersonnalisationJeton(REGLAGES).customize(JwtEncodingContext.with(JwsHeader.with(SignatureAlgorithm.RS256), revendications)
                .registeredClient(client).tokenType(new OAuth2TokenType("refresh_token")).build());
        assertNull(revendications.build().getClaims().get("org"));
    }

    @Test
    void un_jeton_sans_audience_est_refuse_par_l_api() {
        Jwt sansAudience = Jwt.withTokenValue("t").header("alg", "RS256").subject("s").build();
        assertTrue(ServeurAutorisationConfiguration.audience("https://api.essai").validate(sansAudience).hasErrors());
    }
}
