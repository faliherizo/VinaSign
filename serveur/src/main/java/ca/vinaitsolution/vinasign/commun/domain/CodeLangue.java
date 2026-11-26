package ca.vinaitsolution.vinasign.commun.domain;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Code de langue ISO 639-1. Seul le format est validé ici : les langues offertes sont un référentiel
 * (type {@code langue}), jamais une liste figée dans le code.
 */
public record CodeLangue(String valeur) {

    private static final Pattern FORMAT = Pattern.compile("[a-z]{2}");

    public CodeLangue {
        if (valeur == null || !FORMAT.matcher(valeur.strip().toLowerCase(Locale.ROOT)).matches()) {
            throw new IllegalArgumentException("Code de langue : deux lettres ISO 639-1 attendues.");
        }
        valeur = valeur.strip().toLowerCase(Locale.ROOT);
    }
}
