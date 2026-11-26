package ca.vinaitsolution.vinasign.commun.infrastructure.securite;

import ca.vinaitsolution.vinasign.commun.application.port.ContexteAccesCourant;
import ca.vinaitsolution.vinasign.commun.domain.ContexteAcces;
import java.util.Optional;
import java.util.Set;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

/**
 * Organisation et application cliente tirées du jeton validé (revendications {@code org} et {@code client_id}). Un
 * utilisateur du portail au rôle {@code preparateur} ou {@code auditeur}, sans application, agit dans l'espace réservé
 * {@code portail} de son organisation (VS-ADR-009) ; ce que chacun y fait (préparer, ou seulement lire) relève des routes.
 */
@Component
public class ContexteAccesJeton implements ContexteAccesCourant {

    public static final String REVENDICATION_CLIENT = "client_id";
    static final Set<String> ROLES_PORTAIL = Set.of("ROLE_preparateur", "ROLE_auditeur");

    private final OrganisationCouranteJeton organisation;

    public ContexteAccesJeton(OrganisationCouranteJeton organisation) {
        this.organisation = organisation;
    }

    @Override
    public ContexteAcces courant() {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        Optional<String> client = a instanceof JwtAuthenticationToken jeton
                ? Optional.ofNullable(jeton.getToken().getClaimAsString(REVENDICATION_CLIENT)) : Optional.empty();
        if (client.isEmpty() && a != null && a.getAuthorities().stream().anyMatch(g -> ROLES_PORTAIL.contains(g.getAuthority()))) {
            client = Optional.of(ContexteAcces.CLIENT_PORTAIL);
        }
        return new ContexteAcces(organisation.courante(), client);
    }
}
