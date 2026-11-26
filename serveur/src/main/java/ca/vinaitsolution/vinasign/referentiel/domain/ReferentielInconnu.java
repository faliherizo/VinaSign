package ca.vinaitsolution.vinasign.referentiel.domain;

/** Aucun type de référentiel ne porte ce code. */
public class ReferentielInconnu extends RuntimeException {

    public ReferentielInconnu(CodeReferentiel code) {
        super("Référentiel inconnu : " + code.valeur() + ".");
    }
}
