package ca.vinaitsolution.vinasign.integration.domain;

import ca.vinaitsolution.vinasign.commun.domain.ErreurMetier;

/** Destination de webhook refusée : URL invalide ou interne (anti-SSRF), événement inconnu. */
public class DestinationInvalide extends ErreurMetier {

    public DestinationInvalide(String detail) {
        super(Nature.INVALIDE, "destination-webhook-invalide", detail);
    }
}
