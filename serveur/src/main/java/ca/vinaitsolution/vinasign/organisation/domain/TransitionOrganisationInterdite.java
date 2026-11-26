package ca.vinaitsolution.vinasign.organisation.domain;

import ca.vinaitsolution.vinasign.commun.domain.ErreurMetier;

public class TransitionOrganisationInterdite extends ErreurMetier {

    public TransitionOrganisationInterdite(String message) {
        super(Nature.CONFLIT, "transition-organisation-interdite", message);
    }
}
