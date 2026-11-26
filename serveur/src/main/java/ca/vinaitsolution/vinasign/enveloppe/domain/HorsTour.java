package ca.vinaitsolution.vinasign.enveloppe.domain;

import ca.vinaitsolution.vinasign.commun.domain.ErreurMetier;

/** Un signataire ne signe pas avant son tour (VS-023). */
public class HorsTour extends ErreurMetier {

    public HorsTour() {
        super(Nature.CONFLIT, "ordre-de-signature", "Ce signataire ne peut pas encore agir : un signataire le précède.");
    }
}
