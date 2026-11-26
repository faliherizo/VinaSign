package ca.vinaitsolution.vinasign.commun.infrastructure.securite;

import ca.vinaitsolution.vinasign.commun.domain.IdentifiantOrganisation;
import java.util.List;
import org.springframework.security.authentication.AbstractAuthenticationToken;

/** Tâche de la plateforme agissant pour une organisation : aucune autorité d'API, seulement le contexte de la RLS. */
public class AuthentificationTache extends AbstractAuthenticationToken {

    private final IdentifiantOrganisation organisation;
    private final String acteur;

    public AuthentificationTache(IdentifiantOrganisation organisation, String acteur) {
        super(List.of());
        this.organisation = organisation;
        this.acteur = acteur;
        setAuthenticated(true);
    }

    public IdentifiantOrganisation organisation() {
        return organisation;
    }

    @Override
    public Object getCredentials() {
        return null;
    }

    @Override
    public Object getPrincipal() {
        return acteur;
    }
}
