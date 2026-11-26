package ca.vinaitsolution.vinasign.referentiel;

import ca.vinaitsolution.vinasign.referentiel.application.port.Referentiels;
import ca.vinaitsolution.vinasign.referentiel.domain.TypeReferentiel;
import ca.vinaitsolution.vinasign.support.ReferentielsEnMemoire;

class ReferentielsEnMemoireContratTest extends ContratReferentiels {

    private final ReferentielsEnMemoire depot = new ReferentielsEnMemoire();

    @Override
    protected Referentiels referentiels() {
        return depot;
    }

    @Override
    protected void enregistrer(TypeReferentiel type) {
        depot.avec(type);
    }
}
