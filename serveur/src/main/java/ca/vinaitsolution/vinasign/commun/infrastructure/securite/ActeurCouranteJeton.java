package ca.vinaitsolution.vinasign.commun.infrastructure.securite;

import ca.vinaitsolution.vinasign.commun.application.port.ActeurCourant;
import ca.vinaitsolution.vinasign.commun.domain.Acteur;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/** Acteur = nom de l'authentification (sujet du jeton). Sans authentification, aucune action ne peut être attribuée. */
@Component
public class ActeurCouranteJeton implements ActeurCourant {

    @Override
    public Acteur courant() {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        if (a == null || !a.isAuthenticated() || a.getName() == null || a.getName().isBlank()) {
            throw new AuthenticationCredentialsNotFoundException("Action sans acteur authentifié.");
        }
        return new Acteur(a.getName());
    }
}
