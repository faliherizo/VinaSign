package ca.vinaitsolution.vinasign.commun.infrastructure.securite;

import ca.vinaitsolution.vinasign.commun.domain.IdentifiantOrganisation;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/**
 * Utilisateur du portail authentifié (VS-014) : son organisation et ses rôles viennent de la base, pas du jeton du
 * fournisseur d'identité. Son nom d'acteur est un identifiant opaque, jamais son courriel.
 */
public class AuthentificationUtilisateur extends JwtAuthenticationToken {

    private final transient Optional<IdentifiantOrganisation> organisation;

    public AuthentificationUtilisateur(Jwt jeton, UUID utilisateur, Optional<IdentifiantOrganisation> organisation,
                                       Collection<? extends GrantedAuthority> autorites) {
        super(jeton, autorites, "utilisateur:" + utilisateur);
        this.organisation = organisation;
    }

    public Optional<IdentifiantOrganisation> organisation() {
        return organisation;
    }
}
