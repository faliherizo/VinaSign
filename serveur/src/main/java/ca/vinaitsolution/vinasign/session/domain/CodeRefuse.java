package ca.vinaitsolution.vinasign.session.domain;

import ca.vinaitsolution.vinasign.commun.domain.ErreurMetier;

/** Code à usage unique refusé (déjà utilisé, verrouillé, expiré) : le type dit quoi faire. */
public class CodeRefuse extends ErreurMetier {

    public CodeRefuse(String type, String message) {
        super(Nature.CONFLIT, type, message);
    }
}
