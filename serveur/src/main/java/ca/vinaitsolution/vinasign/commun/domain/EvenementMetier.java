package ca.vinaitsolution.vinasign.commun.domain;

import java.util.Map;

/**
 * Fait publié d'un contexte à l'autre (un contexte ne lit jamais les entités d'un autre) : son type (code du référentiel
 * de son contexte), son organisation et des attributs opaques (identifiants, codes), jamais un renseignement personnel.
 */
public record EvenementMetier(String type, IdentifiantOrganisation organisation, Map<String, String> attributs) {

    public EvenementMetier {
        if (type == null || type.isBlank() || organisation == null) {
            throw new IllegalArgumentException("Événement métier incomplet.");
        }
        attributs = attributs == null ? Map.of() : Map.copyOf(attributs);
    }
}
