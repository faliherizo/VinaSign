package ca.vinaitsolution.vinasign.acces.domain;

import ca.vinaitsolution.vinasign.commun.domain.ErreurMetier;

public class CourrielDejaInscrit extends ErreurMetier {

    public CourrielDejaInscrit() {
        super(Nature.CONFLIT, "courriel-deja-inscrit", "Un utilisateur porte déjà ce courriel.");
    }
}
