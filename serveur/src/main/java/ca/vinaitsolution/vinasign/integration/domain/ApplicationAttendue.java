package ca.vinaitsolution.vinasign.integration.domain;

import ca.vinaitsolution.vinasign.commun.domain.ErreurMetier;

/** Les webhooks appartiennent à une application cliente : un appel sans application est refusé. */
public class ApplicationAttendue extends ErreurMetier {

    public ApplicationAttendue() {
        super(Nature.INTERDIT, "application-requise", "Seule une application cliente gère ses webhooks.");
    }
}
