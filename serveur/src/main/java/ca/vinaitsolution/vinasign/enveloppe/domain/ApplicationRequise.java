package ca.vinaitsolution.vinasign.enveloppe.domain;

import ca.vinaitsolution.vinasign.commun.domain.ErreurMetier;

/** Les enveloppes de l'API sont créées et pilotées par une application cliente, au nom de son organisation. */
public class ApplicationRequise extends ErreurMetier {

    public ApplicationRequise() {
        super(Nature.INTERDIT, "application-requise", "Cette action est réservée à une application cliente d'une organisation.");
    }
}
