package ca.vinaitsolution.vinasign.commun.application.port;

import ca.vinaitsolution.vinasign.commun.domain.ContexteAcces;

/** Contexte d'accès de la requête en cours, tiré de l'authentification. */
public interface ContexteAccesCourant {

    ContexteAcces courant();
}
