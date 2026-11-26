package ca.vinaitsolution.vinasign.commun.infrastructure.web;

import ca.vinaitsolution.vinasign.referentiel.domain.ReferentielInconnu;
import ca.vinaitsolution.vinasign.referentiel.domain.ValeurReferentielInconnue;
import ca.vinaitsolution.vinasign.commun.domain.ErreurMetier;
import java.net.URI;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Erreurs au format RFC 9457 ({@code application/problem+json}), avec un type stable par cas : un client réagit au type,
 * jamais au texte. Aucune trace ni détail interne n'est renvoyé.
 */
@RestControllerAdvice
public class GestionErreurs {

    static final String BASE = "https://vinasign.ca/problemes/";

    @ExceptionHandler(ReferentielInconnu.class)
    ProblemDetail introuvable(ReferentielInconnu e) {
        return probleme(HttpStatus.NOT_FOUND, "referentiel-inconnu", e.getMessage());
    }

    @ExceptionHandler(ValeurReferentielInconnue.class)
    ProblemDetail valeurInconnue(ValeurReferentielInconnue e) {
        return probleme(HttpStatus.UNPROCESSABLE_ENTITY, "valeur-referentiel-inconnue", e.getMessage());
    }

    @ExceptionHandler(ErreurMetier.class)
    ProblemDetail metier(ErreurMetier e) {
        HttpStatus statut = switch (e.nature()) {
            case INTROUVABLE -> HttpStatus.NOT_FOUND;
            case CONFLIT -> HttpStatus.CONFLICT;
            case INVALIDE -> HttpStatus.UNPROCESSABLE_ENTITY;
            case INTERDIT -> HttpStatus.FORBIDDEN;
            case PERIME -> HttpStatus.GONE;
        };
        return probleme(statut, e.type(), e.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ProblemDetail requeteInvalide(IllegalArgumentException e) {
        return probleme(HttpStatus.BAD_REQUEST, "requete-invalide", e.getMessage());
    }

    static ProblemDetail probleme(HttpStatus statut, String type, String detail) {
        ProblemDetail p = ProblemDetail.forStatusAndDetail(statut, detail);
        p.setType(URI.create(BASE + type));
        return p;
    }
}
