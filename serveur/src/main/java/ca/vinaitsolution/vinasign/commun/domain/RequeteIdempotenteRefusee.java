package ca.vinaitsolution.vinasign.commun.domain;

/** Clé d'idempotence réutilisée avec un autre corps, ou requête identique encore en cours. */
public class RequeteIdempotenteRefusee extends ErreurMetier {

    public RequeteIdempotenteRefusee(Nature nature, String type, String message) {
        super(nature, type, message);
    }
}
