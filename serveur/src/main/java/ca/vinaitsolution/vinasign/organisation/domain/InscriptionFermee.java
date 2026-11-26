package ca.vinaitsolution.vinasign.organisation.domain;

import ca.vinaitsolution.vinasign.commun.domain.ErreurMetier;

/** L'inscription en libre-service n'est pas ouverte sur cette plateforme (VS-122). */
public class InscriptionFermee extends ErreurMetier {

    public InscriptionFermee() {
        super(Nature.INTERDIT, "inscription-fermee", "L'inscription en libre-service n'est pas ouverte sur cette plateforme ; communiquez avec nous.");
    }
}
