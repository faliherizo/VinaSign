package ca.vinaitsolution.vinasign.enveloppe.domain;

import ca.vinaitsolution.vinasign.commun.domain.ErreurMetier;

public class TransitionEnveloppeInterdite extends ErreurMetier {

    public TransitionEnveloppeInterdite(String message) {
        super(Nature.CONFLIT, "transition-enveloppe-interdite", message);
    }
}
