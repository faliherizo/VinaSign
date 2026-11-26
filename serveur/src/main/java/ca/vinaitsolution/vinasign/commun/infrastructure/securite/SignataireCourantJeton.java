package ca.vinaitsolution.vinasign.commun.infrastructure.securite;

import ca.vinaitsolution.vinasign.commun.application.port.SignataireCourant;
import ca.vinaitsolution.vinasign.commun.domain.IdentiteSignataire;
import java.util.Optional;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/** Signataire tiré de l'authentification par session (jamais d'un paramètre fourni par le client). */
@Component
public class SignataireCourantJeton implements SignataireCourant {

    @Override
    public Optional<IdentiteSignataire> courant() {
        if (SecurityContextHolder.getContext().getAuthentication() instanceof AuthentificationSignataire a && a.session() != null) {
            return Optional.of(new IdentiteSignataire(a.organisation(), a.enveloppe(), a.signataire(), a.session()));
        }
        return Optional.empty();
    }
}
