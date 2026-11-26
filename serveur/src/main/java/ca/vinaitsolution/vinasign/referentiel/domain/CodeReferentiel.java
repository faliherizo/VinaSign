package ca.vinaitsolution.vinasign.referentiel.domain;

import java.util.regex.Pattern;

/** Code stable d'un type ou d'une valeur de référentiel : c'est lui, jamais un identifiant, que le code et les tests citent. */
public record CodeReferentiel(String valeur) {

    private static final Pattern FORMAT = Pattern.compile("[a-z0-9][a-z0-9-]{0,59}");

    public CodeReferentiel {
        if (valeur == null || !FORMAT.matcher(valeur).matches()) {
            throw new CodeReferentielInvalide(valeur);
        }
    }

    /** Code mal formé : minuscules, chiffres et tirets, 1 à 60 caractères. */
    public static class CodeReferentielInvalide extends IllegalArgumentException {
        public CodeReferentielInvalide(String saisi) {
            super("Code de référentiel invalide : minuscules, chiffres et tirets, 1 à 60 caractères (reçu : "
                    + (saisi == null ? "rien" : saisi.length() > 60 ? saisi.substring(0, 60) + "…" : saisi) + ").");
        }
    }
}
