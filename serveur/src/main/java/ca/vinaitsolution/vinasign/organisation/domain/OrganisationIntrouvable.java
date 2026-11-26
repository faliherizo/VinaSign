package ca.vinaitsolution.vinasign.organisation.domain;

import ca.vinaitsolution.vinasign.commun.domain.ErreurMetier;

public class OrganisationIntrouvable extends ErreurMetier {

    public OrganisationIntrouvable() {
        super(Nature.INTROUVABLE, "organisation-introuvable", "Organisation introuvable.");
    }
}
