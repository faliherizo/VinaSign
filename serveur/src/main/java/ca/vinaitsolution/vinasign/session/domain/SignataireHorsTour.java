package ca.vinaitsolution.vinasign.session.domain;

import ca.vinaitsolution.vinasign.commun.domain.ErreurMetier;

/** On n'ouvre de session que pour un signataire dont c'est le tour. */
public class SignataireHorsTour extends ErreurMetier {

    public SignataireHorsTour() {
        super(Nature.CONFLIT, "ordre-de-signature", "Ce signataire ne peut pas encore agir : un signataire le précède.");
    }
}
