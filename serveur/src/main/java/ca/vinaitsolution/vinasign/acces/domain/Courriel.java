package ca.vinaitsolution.vinasign.acces.domain;

import java.util.Locale;
import java.util.regex.Pattern;

/** Adresse de courriel d'un utilisateur du portail, en minuscules : renseignement personnel, jamais journalisé. */
public record Courriel(String valeur) {

    private static final Pattern FORME = Pattern.compile("[^@\\s]{1,64}@[^@\\s]+\\.[^@\\s.]{2,}");

    public Courriel {
        valeur = valeur == null ? "" : valeur.strip().toLowerCase(Locale.ROOT);
        if (valeur.length() > 320 || !FORME.matcher(valeur).matches()) {
            throw new IllegalArgumentException("Adresse de courriel invalide.");
        }
    }

    /** Sans l'adresse : un {@code toString} ne doit pas la répandre dans une trace ou un message. */
    @Override
    public String toString() {
        return "Courriel[masqué]";
    }
}
