package ca.vinaitsolution.vinasign.referentiel.domain;

import ca.vinaitsolution.vinasign.commun.domain.Libelles;
import java.util.Map;

/**
 * Valeur d'un référentiel : un élément d'une liste métier (une langue, un type de champ, un niveau d'authentification).
 * Les attributs portent les propriétés propres au type (ex. le rang d'un niveau), sans changer le schéma.
 */
public record ValeurReferentiel(CodeReferentiel code, Libelles libelles, int ordre, Map<String, String> attributs, boolean actif) {

    public ValeurReferentiel {
        if (code == null || libelles == null) {
            throw new IllegalArgumentException("Une valeur de référentiel a un code et des libellés.");
        }
        attributs = attributs == null ? Map.of() : Map.copyOf(attributs);
    }
}
