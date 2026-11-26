package ca.vinaitsolution.vinasign.session.domain;

import ca.vinaitsolution.vinasign.commun.domain.ErreurMetier;

/** Aucune signature sans consentement explicite (VS-041). */
public class ConsentementRequis extends ErreurMetier {

    public ConsentementRequis() {
        super(Nature.INTERDIT, "consentement-requis", "Accepter le consentement à signer électroniquement avant de signer.");
    }
}
