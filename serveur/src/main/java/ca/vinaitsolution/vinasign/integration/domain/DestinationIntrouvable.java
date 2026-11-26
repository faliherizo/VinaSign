package ca.vinaitsolution.vinasign.integration.domain;

import ca.vinaitsolution.vinasign.commun.domain.ErreurMetier;

/** Destination inconnue de l'appelant (une destination d'une autre application n'est pas révélée). */
public class DestinationIntrouvable extends ErreurMetier {

    public DestinationIntrouvable() {
        super(Nature.INTROUVABLE, "destination-webhook-introuvable", "Destination de webhook introuvable.");
    }
}
