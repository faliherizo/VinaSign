package ca.vinaitsolution.vinasign.acces.domain;

import ca.vinaitsolution.vinasign.commun.domain.ErreurMetier;

public class TransitionApplicationInterdite extends ErreurMetier {

    public TransitionApplicationInterdite(String message) {
        super(Nature.CONFLIT, "transition-application-interdite", message);
    }
}
