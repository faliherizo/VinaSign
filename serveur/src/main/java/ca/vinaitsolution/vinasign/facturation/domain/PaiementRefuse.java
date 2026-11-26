package ca.vinaitsolution.vinasign.facturation.domain;

import ca.vinaitsolution.vinasign.commun.domain.ErreurMetier;

/** Événement du prestataire refusé : signature invalide, trop ancien, illisible (VS-121). */
public class PaiementRefuse extends ErreurMetier {

    public PaiementRefuse(String detail) {
        super(Nature.INTERDIT, "paiement-refuse", detail);
    }
}
