package ca.vinaitsolution.vinasign.acces.domain;

import ca.vinaitsolution.vinasign.commun.domain.ErreurMetier;

/** On n'invite personne dans une organisation inconnue ou suspendue. */
public class OrganisationInactive extends ErreurMetier {

    public OrganisationInactive() {
        super(Nature.CONFLIT, "organisation-inactive", "L'organisation est inconnue ou suspendue.");
    }
}
