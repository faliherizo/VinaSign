package ca.vinaitsolution.vinasign.referentiel.domain;

/** La valeur saisie n'existe pas, ou plus, dans le référentiel : la saisie est refusée et le message dit laquelle. */
public class ValeurReferentielInconnue extends RuntimeException {

    public ValeurReferentielInconnue(CodeReferentiel type, CodeReferentiel valeur) {
        super("Valeur « " + valeur.valeur() + " » absente du référentiel « " + type.valeur() + " ».");
    }
}
