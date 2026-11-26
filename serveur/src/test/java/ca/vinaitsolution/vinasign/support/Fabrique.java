package ca.vinaitsolution.vinasign.support;

import ca.vinaitsolution.vinasign.commun.domain.Libelles;
import ca.vinaitsolution.vinasign.referentiel.domain.CodeReferentiel;
import ca.vinaitsolution.vinasign.referentiel.domain.TypeReferentiel;
import ca.vinaitsolution.vinasign.referentiel.domain.ValeurReferentiel;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Données de test générées : codes uniques à chaque appel, jamais d'identifiant ni de code de production en dur. */
public final class Fabrique {

    private Fabrique() {
    }

    public static String codeUnique(String prefixe) {
        return prefixe + "-" + UUID.randomUUID().toString().substring(0, 8);
    }

    public static ValeurReferentiel valeur(String code, int ordre, boolean actif) {
        return new ValeurReferentiel(new CodeReferentiel(code), Libelles.de(Map.of("fr", "Libellé " + code, "en", "Label " + code)),
                ordre, Map.of("rang", String.valueOf(ordre)), actif);
    }

    public static TypeReferentiel type(String code, ValeurReferentiel... valeurs) {
        return new TypeReferentiel(new CodeReferentiel(code), Libelles.de(Map.of("fr", "Type " + code, "en", "Kind " + code)),
                List.of(valeurs));
    }
}
