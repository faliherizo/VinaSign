package ca.vinaitsolution.vinasign.enveloppe.domain;

import ca.vinaitsolution.vinasign.commun.domain.ErreurMetier;

/** Saisie cohérente en forme mais refusée par une règle métier : le message dit quoi corriger. */
public class SaisieInvalide extends ErreurMetier {

    public SaisieInvalide(String type, String message) {
        super(Nature.INVALIDE, type, message);
    }
}
