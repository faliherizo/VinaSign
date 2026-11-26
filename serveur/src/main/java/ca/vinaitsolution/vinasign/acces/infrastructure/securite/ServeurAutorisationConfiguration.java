package ca.vinaitsolution.vinasign.acces.infrastructure.securite;

import ca.vinaitsolution.vinasign.commun.infrastructure.web.FiltreLimiteDebit;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.authorization.authentication.JwtClientAssertionAuthenticationProvider;
import org.springframework.security.oauth2.server.authorization.config.annotation.web.configuration.OAuth2AuthorizationServerConfiguration;
import org.springframework.security.oauth2.server.authorization.config.annotation.web.configurers.OAuth2AuthorizationServerConfigurer;
import org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Serveur d'autorisation OAuth 2.0 (VS-012) : client credentials, authentification private_key_jwt, jetons JWT signés.
 * Il expose /oauth2/token, /oauth2/jwks et les métadonnées ; l'API (chaîne suivante) n'accepte que ses jetons, de la
 * bonne audience.
 */
@Configuration
@EnableScheduling
public class ServeurAutorisationConfiguration {

    @Bean
    @Order(1)
    SecurityFilterChain serveurAutorisation(HttpSecurity http, DecodeurAssertionsClient assertions, FiltreLimiteDebit debit)
            throws Exception {
        OAuth2AuthorizationServerConfigurer serveur = OAuth2AuthorizationServerConfigurer.authorizationServer();
        return http
                .securityMatcher(serveur.getEndpointsMatcher())
                // Le point de jeton est limité par adresse avant toute vérification d'assertion (VS-015).
                .addFilterBefore(debit, org.springframework.security.web.context.SecurityContextHolderFilter.class)
                .with(serveur, s -> s.clientAuthentication(c -> c.authenticationProviders(fournisseurs -> fournisseurs.forEach(f -> {
                    if (f instanceof JwtClientAssertionAuthenticationProvider p) {
                        p.setJwtDecoderFactory(assertions);
                    }
                }))))
                .authorizeHttpRequests(a -> a.anyRequest().authenticated())
                .csrf(c -> c.ignoringRequestMatchers(serveur.getEndpointsMatcher()))
                .build();
    }

    @Bean
    AuthorizationServerSettings reglagesServeur(JetonsProprietes proprietes) {
        return AuthorizationServerSettings.builder().issuer(proprietes.emetteur()).build();
    }

    /** Vérification des jetons d'accès par l'API : signature, expiration, émetteur et audience. */
    @Bean
    JwtDecoder decodeurJetons(JWKSource<SecurityContext> cles, JetonsProprietes proprietes) {
        NimbusJwtDecoder decodeur = (NimbusJwtDecoder) OAuth2AuthorizationServerConfiguration.jwtDecoder(cles);
        decodeur.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(proprietes.emetteur()), audience(proprietes.audience())));
        return decodeur;
    }

    static OAuth2TokenValidator<Jwt> audience(String attendue) {
        return jeton -> {
            List<String> audiences = jeton.getAudience();
            return audiences != null && audiences.contains(attendue) ? OAuth2TokenValidatorResult.success()
                    : OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "Audience incorrecte.", null));
        };
    }
}
