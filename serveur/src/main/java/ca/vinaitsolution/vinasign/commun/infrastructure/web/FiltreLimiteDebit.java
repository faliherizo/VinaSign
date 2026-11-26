package ca.vinaitsolution.vinasign.commun.infrastructure.web;

import ca.vinaitsolution.vinasign.commun.application.LimiterDebit;
import ca.vinaitsolution.vinasign.commun.application.port.ContexteAccesCourant;
import ca.vinaitsolution.vinasign.commun.domain.ContexteAcces;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Limitation du débit (VS-015) : au-delà du plan, 429 avec {@code Retry-After} (secondes) et un problème RFC 9457.
 * Chaque requête est comptée dans {@code vinasign.api.requetes}, par application cliente (les utilisateurs et les
 * requêtes anonymes sont regroupés pour borner la cardinalité) et par issue.
 */
public class FiltreLimiteDebit extends OncePerRequestFilter {

    static final String METRIQUE = "vinasign.api.requetes";

    private final LimiterDebit limiteur;
    private final ContexteAccesCourant contexte;
    private final MeterRegistry metriques;
    private final ObjectMapper json;

    public FiltreLimiteDebit(LimiterDebit limiteur, ContexteAccesCourant contexte, MeterRegistry metriques, ObjectMapper json) {
        this.limiteur = limiteur;
        this.contexte = contexte;
        this.metriques = metriques;
        this.json = json;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest requete, HttpServletResponse reponse, FilterChain suite)
            throws ServletException, IOException {
        ContexteAcces courant = contexte.courant();
        LimiterDebit.Reponse r = limiteur.passer(new LimiterDebit.Demande(demandeur(courant, requete), courant));
        metriques.counter(METRIQUE, "application", courant.client().orElse(estAuthentifie() ? "utilisateur" : "anonyme"),
                "issue", r.acceptee() ? "acceptee" : "limitee").increment();
        if (!r.acceptee()) {
            reponse.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            reponse.setHeader(HttpHeaders.RETRY_AFTER, String.valueOf(Math.max(1, (r.attente().toMillis() + 999) / 1000)));
            reponse.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
            json.writeValue(reponse.getOutputStream(), GestionErreurs.probleme(HttpStatus.TOO_MANY_REQUESTS, "trop-de-requetes",
                    "Débit du plan dépassé : réessayer après le délai indiqué par Retry-After."));
            return;
        }
        suite.doFilter(requete, reponse);
    }

    private static String demandeur(ContexteAcces courant, HttpServletRequest requete) {
        if (courant.client().isPresent()) {
            return "application:" + courant.client().get();
        }
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        return estAuthentifie() ? "acteur:" + a.getName() : "adresse:" + requete.getRemoteAddr();
    }

    private static boolean estAuthentifie() {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        return a != null && a.isAuthenticated() && !(a instanceof AnonymousAuthenticationToken);
    }
}
