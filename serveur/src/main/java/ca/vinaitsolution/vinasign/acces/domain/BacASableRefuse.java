package ca.vinaitsolution.vinasign.acces.domain;

import ca.vinaitsolution.vinasign.commun.domain.ErreurMetier;

/** Réinitialisation refusée : plateforme hors bac à sable, ou application d'un environnement non réinitialisable. */
public class BacASableRefuse extends ErreurMetier {

    public BacASableRefuse(String detail) {
        super(Nature.INTERDIT, "bac-a-sable-requis", detail);
    }
}
