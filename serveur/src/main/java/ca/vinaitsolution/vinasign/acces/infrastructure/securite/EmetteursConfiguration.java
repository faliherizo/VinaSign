package ca.vinaitsolution.vinasign.acces.infrastructure.securite;

import ca.vinaitsolution.vinasign.acces.application.AuthentifierUtilisateur;
import ca.vinaitsolution.vinasign.acces.application.port.AnnuaireConnexion;
import ca.vinaitsolution.vinasign.commun.infrastructure.securite.ConversionJeton;
import ca.vinaitsolution.vinasign.referentiel.application.port.Referentiels;
import jakarta.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationManagerResolver;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationProvider;
import org.springframework.security.oauth2.server.resource.authentication.JwtIssuerAuthenticationManagerResolver;

/**
 * Deux émetteurs reconnus par l'API (VS-012, VS-014) : VinaSign lui-même (applications clientes) et le fournisseur
 * d'identité du portail (utilisateurs). Un jeton d'un autre émetteur est refusé (401). Chacun a son décodeur, ses
 * contrôles d'audience et sa conversion en autorités.
 */
@Configuration
@EnableConfigurationProperties(PortailProprietes.class)
public class EmetteursConfiguration {

    @Bean
    AuthentifierUtilisateur authentifierUtilisateur(AnnuaireConnexion annuaire, Referentiels referentiels, PortailProprietes portail) {
        return new AuthentifierUtilisateur(annuaire, referentiels, new HashSet<>(portail.methodesMultifacteur()));
    }

    /** Décodeur du fournisseur : signature par ses clés publiées, émetteur et audience exigés. */
    @Bean
    DecodeurPortail decodeurPortail(PortailProprietes portail) {
        if (!portail.estConfigure()) {
            return new DecodeurPortail(null);
        }
        if (portail.audience() == null || portail.audience().isBlank() || portail.jwkSetUri() == null || portail.jwkSetUri().isBlank()) {
            throw new IllegalStateException("Fournisseur d'identité du portail incomplet : VINASIGN_PORTAIL_AUDIENCE et "
                    + "VINASIGN_PORTAIL_JWKS sont requis avec VINASIGN_PORTAIL_EMETTEUR.");
        }
        NimbusJwtDecoder decodeur = NimbusJwtDecoder.withJwkSetUri(portail.jwkSetUri()).build();
        decodeur.setJwtValidator(new DelegatingOAuth2TokenValidator<>(JwtValidators.createDefaultWithIssuer(portail.emetteur()),
                ServeurAutorisationConfiguration.audience(portail.audience())));
        return new DecodeurPortail(decodeur);
    }

    @Bean
    AuthenticationManagerResolver<HttpServletRequest> emetteursReconnus(JwtDecoder decodeurJetons, JetonsProprietes jetons,
                                                                        DecodeurPortail decodeurPortail, PortailProprietes portail,
                                                                        AuthentifierUtilisateur authentification) {
        Map<String, AuthenticationManager> parEmetteur = new HashMap<>();
        JwtAuthenticationProvider applications = new JwtAuthenticationProvider(decodeurJetons);
        applications.setJwtAuthenticationConverter(ConversionJeton.pourVinaSign());
        parEmetteur.put(jetons.emetteur(), applications::authenticate);
        if (portail.estConfigure() && decodeurPortail.decodeur() != null) {
            JwtAuthenticationProvider utilisateurs = new JwtAuthenticationProvider(decodeurPortail.decodeur());
            utilisateurs.setJwtAuthenticationConverter(new ConversionUtilisateurPortail(authentification));
            parEmetteur.put(portail.emetteur(), utilisateurs::authenticate);
        }
        return new JwtIssuerAuthenticationManagerResolver(parEmetteur::get);
    }
}
