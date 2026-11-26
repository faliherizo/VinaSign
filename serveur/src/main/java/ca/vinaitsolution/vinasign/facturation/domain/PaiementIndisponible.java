package ca.vinaitsolution.vinasign.facturation.domain;

import ca.vinaitsolution.vinasign.commun.domain.ErreurMetier;

/** Aucun prestataire de paiement configuré, ou plan sans prix chez lui (VS-121). */
public class PaiementIndisponible extends ErreurMetier {

    public PaiementIndisponible(String detail) {
        super(Nature.INVALIDE, "paiement-indisponible", detail);
    }
}
