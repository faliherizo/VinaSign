package ca.vinaitsolution.vinasign.acces.infrastructure.securite;

import ca.vinaitsolution.vinasign.acces.application.port.AssertionsConsommees;
import ca.vinaitsolution.vinasign.acces.domain.IdentifiantClient;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.proc.JWSVerificationKeySelector;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jwt.proc.DefaultJWTProcessor;
import java.text.ParseException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.MappedJwtClaimSetConverter;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtDecoderFactory;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.stereotype.Component;

/**
 * Vérification d'une assertion client (private_key_jwt, RFC 7523) : signature par une clé publique déclarée de
 * l'application (RS256, ES256), {@code iss} et {@code sub} égaux au client, audience = ce serveur, durée bornée,
 * puis consommation du {@code jti}. Une assertion ne sert qu'une fois.
 */
@Component
class DecodeurAssertionsClient implements JwtDecoderFactory<RegisteredClient> {

    static final Set<JWSAlgorithm> ALGORITHMES = Set.of(JWSAlgorithm.RS256, JWSAlgorithm.ES256);
    private static final String POINT_JETON = "/oauth2/token";

    private final AssertionsConsommees assertions;
    private final JetonsProprietes proprietes;
    private final Clock horloge;

    DecodeurAssertionsClient(AssertionsConsommees assertions, JetonsProprietes proprietes, Clock horloge) {
        this.assertions = assertions;
        this.proprietes = proprietes;
        this.horloge = horloge;
    }

    @Override
    public JwtDecoder createDecoder(RegisteredClient client) {
        JWKSet cles;
        try {
            cles = JWKSet.parse(client.getClientSettings().<String>getSetting(ClientsEnregistres.CLES_PUBLIQUES));
        } catch (ParseException | RuntimeException e) {
            return jeton -> { throw new BadJwtException("Clés publiques de l'application illisibles."); };
        }
        DefaultJWTProcessor<SecurityContext> processeur = new DefaultJWTProcessor<>();
        processeur.setJWSKeySelector(new JWSVerificationKeySelector<>(ALGORITHMES, new ImmutableJWKSet<>(cles)));
        processeur.setJWTClaimsSetVerifier((revendications, contexte) -> { });
        NimbusJwtDecoder decodeur = new NimbusJwtDecoder(processeur);
        // Dans une assertion client, iss est le client_id, pas une URL : il reste du texte (sinon la conversion par
        // défaut échoue et la requête finirait en erreur interne au lieu d'un refus).
        decodeur.setClaimSetConverter(MappedJwtClaimSetConverter.withDefaults(Map.of(JwtClaimNames.ISS, valeur -> valeur)));
        decodeur.setJwtValidator(assertion -> valider(assertion, client.getClientId()));
        return decodeur;
    }

    /** Contrôles dans l'ordre ; le jti n'est consommé que si tout le reste est valide. */
    OAuth2TokenValidatorResult valider(Jwt assertion, String clientId) {
        Instant maintenant = horloge.instant();
        Instant expire = assertion.getExpiresAt();
        if (!clientId.equals(assertion.getClaimAsString(JwtClaimNames.ISS)) || !clientId.equals(assertion.getSubject())) {
            return refus("iss et sub doivent désigner le client.");
        }
        List<String> audiences = assertion.getAudience() == null ? List.of() : assertion.getAudience();
        if (audiences.stream().noneMatch(a -> a.equals(proprietes.emetteur()) || a.equals(proprietes.emetteur() + POINT_JETON))) {
            return refus("Audience de l'assertion incorrecte.");
        }
        if (expire == null || !expire.isAfter(maintenant.minusSeconds(30))) {
            return refus("Assertion expirée.");
        }
        Instant emise = assertion.getIssuedAt() == null ? maintenant : assertion.getIssuedAt();
        if (Duration.between(emise, expire).compareTo(proprietes.dureeMaxAssertion()) > 0 || emise.isAfter(maintenant.plusSeconds(30))) {
            return refus("Durée de l'assertion hors limites.");
        }
        String jti = assertion.getId();
        if (jti == null || jti.isBlank() || jti.length() > 200) {
            return refus("Identifiant d'assertion (jti) requis.");
        }
        if (!assertions.consommer(jti, new IdentifiantClient(clientId), expire)) {
            return refus("Assertion déjà utilisée.");
        }
        return OAuth2TokenValidatorResult.success();
    }

    private static OAuth2TokenValidatorResult refus(String motif) {
        return OAuth2TokenValidatorResult.failure(new OAuth2Error(OAuth2ErrorCodes.INVALID_CLIENT, motif, null));
    }
}
