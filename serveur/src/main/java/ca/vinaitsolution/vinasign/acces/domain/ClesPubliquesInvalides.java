package ca.vinaitsolution.vinasign.acces.domain;

import ca.vinaitsolution.vinasign.commun.domain.ErreurMetier;

public class ClesPubliquesInvalides extends ErreurMetier {

    public ClesPubliquesInvalides(String motif) {
        super(Nature.INVALIDE, "cles-publiques-invalides", motif);
    }
}
