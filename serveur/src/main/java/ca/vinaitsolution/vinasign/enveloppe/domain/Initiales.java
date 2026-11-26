package ca.vinaitsolution.vinasign.enveloppe.domain;

import java.util.Arrays;
import java.util.Locale;
import java.util.stream.Collectors;

/** Initiales d'une personne, apposées dans ses zones d'initiales : première lettre de chaque partie du nom. */
public final class Initiales {

    private static final int MAX = 5;

    private Initiales() {
    }

    /** Chaîne vide si le nom n'a aucune lettre. */
    public static String de(String nom) {
        return nom == null ? "" : Arrays.stream(nom.split("[^\\p{L}]+")).filter(p -> !p.isEmpty()).limit(MAX)
                .map(p -> p.substring(0, p.offsetByCodePoints(0, 1)).toUpperCase(Locale.ROOT)).collect(Collectors.joining());
    }
}
