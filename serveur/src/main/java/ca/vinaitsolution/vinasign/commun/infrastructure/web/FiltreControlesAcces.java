package ca.vinaitsolution.vinasign.commun.infrastructure.web;

import ca.vinaitsolution.vinasign.commun.application.port.ContexteAccesCourant;
import ca.vinaitsolution.vinasign.commun.application.port.ControleAcces;
import ca.vinaitsolution.vinasign.commun.domain.ContexteAcces;
import ca.vinaitsolution.vinasign.commun.domain.RefusAcces;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Applique les {@link ControleAcces} de tous les contextes à chaque requête authentifiée (VS-010, VS-013) : le premier
 * refus arrête la requête par un problème 403, même si le jeton est encore valide. Une requête anonyme n'engage rien.
 */
public class FiltreControlesAcces extends OncePerRequestFilter {

    private final ContexteAccesCourant contexte;
    private final List<ControleAcces> controles;
    private final ObjectMapper json;

    public FiltreControlesAcces(ContexteAccesCourant contexte, List<ControleAcces> controles, ObjectMapper json) {
        this.contexte = contexte;
        this.controles = List.copyOf(controles);
        this.json = json;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest requete, HttpServletResponse reponse, FilterChain suite)
            throws ServletException, IOException {
        ContexteAcces courant = contexte.courant();
        Optional<RefusAcces> refus = controles.stream().map(c -> c.verifier(courant)).flatMap(Optional::stream).findFirst();
        if (refus.isPresent()) {
            reponse.setStatus(HttpStatus.FORBIDDEN.value());
            reponse.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
            json.writeValue(reponse.getOutputStream(), GestionErreurs.probleme(HttpStatus.FORBIDDEN, refus.get().type(), refus.get().message()));
            return;
        }
        suite.doFilter(requete, reponse);
    }
}
