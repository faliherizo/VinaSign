package ca.vinaitsolution.vinasign.support;

import ca.vinaitsolution.vinasign.acces.infrastructure.securite.DecodeurPortail;
import ca.vinaitsolution.vinasign.acces.infrastructure.securite.PortailProprietes;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

/**
 * Fournisseur d'identité OIDC d'essai : il signe des jetons comme le ferait Entra ID, et le portail VinaSign les vérifie
 * par sa clé publique, avec les mêmes contrôles d'émetteur et d'audience qu'en exploitation.
 */
@TestConfiguration(proxyBeanMethods = false)
public class FournisseurIdentiteDeTest {

    private static final RSAKey CLE = ApplicationsDeTest.nouvelleCle();

    /** Identité présentée par une personne. */
    public record Personne(String sujet, String courriel, boolean courrielVerifie, List<String> amr) {

        public static Personne avecMfa(String courriel) {
            return new Personne("sujet-" + UUID.randomUUID(), courriel, true, List.of("pwd", "mfa"));
        }

        public static Personne sansMfa(String courriel) {
            return new Personne("sujet-" + UUID.randomUUID(), courriel, true, List.of("pwd"));
        }

        public Personne avecAmr(List<String> methodes) {
            return new Personne(sujet, courriel, courrielVerifie, methodes);
        }
    }

    @Bean
    @Primary
    DecodeurPortail decodeurPortailDeTest(PortailProprietes portail) throws JOSEException {
        NimbusJwtDecoder decodeur = NimbusJwtDecoder.withPublicKey(CLE.toRSAPublicKey()).build();
        decodeur.setJwtValidator(new DelegatingOAuth2TokenValidator<>(JwtValidators.createDefaultWithIssuer(portail.emetteur()),
                jeton -> jeton.getAudience().contains(portail.audience())
                        ? org.springframework.security.oauth2.core.OAuth2TokenValidatorResult.success()
                        : org.springframework.security.oauth2.core.OAuth2TokenValidatorResult.failure(
                                new org.springframework.security.oauth2.core.OAuth2Error("invalid_token"))));
        return new DecodeurPortail(decodeur);
    }

    /** Jeton d'accès du fournisseur pour cette personne. */
    public static String jeton(PortailProprietes portail, Personne p) {
        return jeton(portail.emetteur(), portail.audience(), p);
    }

    public static String jeton(String emetteur, String audience, Personne p) {
        try {
            JWTClaimsSet.Builder r = new JWTClaimsSet.Builder().issuer(emetteur).subject(p.sujet()).audience(audience)
                    .claim("email", p.courriel()).claim("email_verified", p.courrielVerifie()).claim("amr", p.amr())
                    .issueTime(new Date()).expirationTime(Date.from(Instant.now().plusSeconds(300)));
            SignedJWT jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(CLE.getKeyID()).build(), r.build());
            jwt.sign(new RSASSASigner(CLE));
            return jwt.serialize();
        } catch (JOSEException e) {
            throw new IllegalStateException(e);
        }
    }
}
