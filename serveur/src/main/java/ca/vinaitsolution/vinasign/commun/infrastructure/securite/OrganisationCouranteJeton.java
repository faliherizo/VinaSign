package ca.vinaitsolution.vinasign.commun.infrastructure.securite;

import ca.vinaitsolution.vinasign.commun.application.port.OrganisationCourante;
import ca.vinaitsolution.vinasign.commun.domain.IdentifiantOrganisation;
import java.util.Optional;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

/**
 * Organisation tirée du jeton validé (revendication {@code org}, posée par le serveur d'autorisation, VS-012), ou de
 * la base pour un utilisateur du portail (VS-014) : un client ne peut pas la choisir. Sans jeton, aucune organisation : seules les données communes sont visibles.
 */
@Component
public class OrganisationCouranteJeton implements OrganisationCourante {

    public static final String REVENDICATION = "org";

    @Override
    public Optional<IdentifiantOrganisation> courante() {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        if (a instanceof AuthentificationUtilisateur utilisateur) {
            return utilisateur.organisation();
        }
        if (a instanceof AuthentificationSignataire signataire) {
            return Optional.of(signataire.organisation());
        }
        if (a instanceof AuthentificationTache tache) {
            return Optional.of(tache.organisation());
        }
        if (a instanceof JwtAuthenticationToken jeton && jeton.getToken().hasClaim(REVENDICATION)) {
            return Optional.of(IdentifiantOrganisation.depuis(jeton.getToken().getClaimAsString(REVENDICATION)));
        }
        return Optional.empty();
    }
}
