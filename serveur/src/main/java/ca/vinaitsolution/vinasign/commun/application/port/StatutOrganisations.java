package ca.vinaitsolution.vinasign.commun.application.port;

import ca.vinaitsolution.vinasign.commun.domain.IdentifiantOrganisation;

/**
 * État d'activité d'une organisation, consulté à chaque requête authentifiée et à chaque émission de jeton (VS-010) :
 * une suspension prend effet à la requête suivante.
 */
public interface StatutOrganisations {

    /** Vrai si l'organisation est suspendue ; une organisation inconnue n'est pas réputée active. */
    boolean estBloquee(IdentifiantOrganisation organisation);
}
