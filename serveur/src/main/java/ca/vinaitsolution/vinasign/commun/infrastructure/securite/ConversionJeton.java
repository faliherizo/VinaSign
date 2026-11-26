package ca.vinaitsolution.vinasign.commun.infrastructure.securite;

import java.util.ArrayList;
import java.util.List;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;

/**
 * Autorités d'un jeton émis par VinaSign : ses portées ({@code SCOPE_…}) et ses rôles ({@code ROLE_…}). Le sujet du
 * jeton devient le nom de l'acteur.
 */
public final class ConversionJeton {

    public static final String REVENDICATION_ROLES = "roles";

    private ConversionJeton() {
    }

    public static JwtAuthenticationConverter pourVinaSign() {
        JwtGrantedAuthoritiesConverter portees = new JwtGrantedAuthoritiesConverter();
        JwtGrantedAuthoritiesConverter roles = new JwtGrantedAuthoritiesConverter();
        roles.setAuthoritiesClaimName(REVENDICATION_ROLES);
        roles.setAuthorityPrefix("ROLE_");
        JwtAuthenticationConverter conversion = new JwtAuthenticationConverter();
        conversion.setJwtGrantedAuthoritiesConverter(jeton -> {
            List<GrantedAuthority> autorites = new ArrayList<>(portees.convert(jeton));
            autorites.addAll(roles.convert(jeton));
            return autorites;
        });
        return conversion;
    }
}
