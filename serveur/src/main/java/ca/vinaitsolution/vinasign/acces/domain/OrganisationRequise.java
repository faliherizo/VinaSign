package ca.vinaitsolution.vinasign.acces.domain;

import ca.vinaitsolution.vinasign.commun.domain.ErreurMetier;

public class OrganisationRequise extends ErreurMetier {

    public OrganisationRequise() {
        super(Nature.INTERDIT, "organisation-requise", "Cette action se fait au nom d'une organisation.");
    }
}
