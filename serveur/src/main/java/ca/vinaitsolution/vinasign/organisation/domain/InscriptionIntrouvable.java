package ca.vinaitsolution.vinasign.organisation.domain;

import ca.vinaitsolution.vinasign.commun.domain.ErreurMetier;

/** Lien ou demande inconnus ; même réponse dans tous les cas, sans rien révéler. */
public class InscriptionIntrouvable extends ErreurMetier {

    public InscriptionIntrouvable() {
        super(Nature.INTROUVABLE, "inscription-introuvable", "Demande d'inscription introuvable.");
    }
}
