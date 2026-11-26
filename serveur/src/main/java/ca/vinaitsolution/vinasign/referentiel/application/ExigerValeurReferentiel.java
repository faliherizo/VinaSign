package ca.vinaitsolution.vinasign.referentiel.application;

import ca.vinaitsolution.vinasign.referentiel.application.port.Referentiels;
import ca.vinaitsolution.vinasign.referentiel.domain.CodeReferentiel;
import ca.vinaitsolution.vinasign.referentiel.domain.ReferentielInconnu;
import ca.vinaitsolution.vinasign.referentiel.domain.ValeurReferentiel;
import ca.vinaitsolution.vinasign.referentiel.domain.ValeurReferentielInconnue;

/**
 * Validation dynamique d'une saisie : les autres contextes vérifient qu'un code (type de champ, niveau, langue…) existe
 * dans son référentiel, au lieu de le comparer à une liste figée dans le code.
 */
public class ExigerValeurReferentiel {

    private final Referentiels referentiels;

    public ExigerValeurReferentiel(Referentiels referentiels) {
        this.referentiels = referentiels;
    }

    public ValeurReferentiel executer(CodeReferentiel type, CodeReferentiel valeur) {
        return referentiels.type(type).orElseThrow(() -> new ReferentielInconnu(type))
                .valeurActive(valeur).orElseThrow(() -> new ValeurReferentielInconnue(type, valeur));
    }
}
