package ca.vinaitsolution.vinasign.commun.application.port;

import ca.vinaitsolution.vinasign.commun.domain.Acteur;

/** Auteur de la requête en cours, tiré de l'authentification. */
public interface ActeurCourant {

    Acteur courant();
}
