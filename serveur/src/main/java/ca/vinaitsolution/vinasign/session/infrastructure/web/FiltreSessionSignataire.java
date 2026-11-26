package ca.vinaitsolution.vinasign.session.infrastructure.web;

import ca.vinaitsolution.vinasign.commun.domain.IdentifiantOrganisation;
import ca.vinaitsolution.vinasign.commun.infrastructure.securite.AuthentificationSignataire;
import ca.vinaitsolution.vinasign.commun.infrastructure.web.FiltreAuthentificationComplementaire;
import ca.vinaitsolution.vinasign.session.application.ServicesSession;
import ca.vinaitsolution.vinasign.session.application.port.SessionsSignataire;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Clock;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Authentification du signataire par sa session (en-tête {@code X-Session-Signataire}) : une session valide pose son
 * organisation, son enveloppe et son identité ; une session inconnue ou expirée n'authentifie rien (401 ensuite).
 */
public class FiltreSessionSignataire extends OncePerRequestFilter implements FiltreAuthentificationComplementaire {

    public static final String ENTETE = "X-Session-Signataire";

    private final SessionsSignataire sessions;
    private final Clock horloge;

    public FiltreSessionSignataire(SessionsSignataire sessions, Clock horloge) {
        this.sessions = sessions;
        this.horloge = horloge;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest requete) {
        return requete.getHeader(ENTETE) == null;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest requete, HttpServletResponse reponse, FilterChain suite)
            throws ServletException, IOException {
        String jeton = requete.getHeader(ENTETE);
        if (jeton.length() <= 100) {
            sessions.session(ServicesSession.empreinte(jeton)).filter(s -> s.valideA(horloge.instant()))
                    .ifPresent(s -> SecurityContextHolder.getContext().setAuthentication(new AuthentificationSignataire(
                            new IdentifiantOrganisation(s.organisation()), s.enveloppe(), s.signataire(), s.id())));
        }
        suite.doFilter(requete, reponse);
    }
}
