package ca.vinaitsolution.vinasign.enveloppe.domain;

import ca.vinaitsolution.vinasign.commun.domain.ErreurMetier;

/** Refus explicite d'un document déposé (VS-021) : le type dit lequel des contrôles a échoué. */
public class DocumentRefuse extends ErreurMetier {

    public DocumentRefuse(String type, String message) {
        super(Nature.INVALIDE, type, message);
    }
}
