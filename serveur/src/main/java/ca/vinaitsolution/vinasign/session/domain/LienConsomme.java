package ca.vinaitsolution.vinasign.session.domain;

import ca.vinaitsolution.vinasign.commun.domain.ErreurMetier;

/** Lien déjà utilisé pour signer ou refuser (VS-040) : rendu en 410 Gone par l'API. */
public class LienConsomme extends ErreurMetier {

    public LienConsomme() {
        super(Nature.PERIME, "lien-consomme", "Ce lien a déjà servi : le document a été signé ou refusé.");
    }
}
