package ca.vinaitsolution.vinasign.commun.infrastructure.journal;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Dernier rempart avant l'écriture d'un journal (VS-004, dossier de sécurité) : remplace courriels, numéros de
 * téléphone, jetons et secrets par une étiquette. Le code ne doit pas les journaliser ; ce masque garantit qu'une erreur
 * de développement ne les expose pas pour autant. Les faux positifs (un nombre de dix chiffres) sont acceptés : un
 * journal masqué à tort coûte moins qu'un renseignement personnel exposé.
 */
public final class MasqueRenseignements {

    private record Regle(Pattern motif, String remplacement) {
    }

    private static final List<Regle> REGLES = List.of(
            new Regle(Pattern.compile("(?i)\\bbearer\\s+[\\w.~+/=-]+"), "Bearer [jeton]"),
            new Regle(Pattern.compile("\\beyJ[\\w-]*\\.[\\w-]+\\.[\\w-]*"), "[jeton]"),
            new Regle(Pattern.compile("(?i)\\b(mot[_-]?de[_-]?passe|mdp|password|passwd|secret|client_secret|otp)\\s*[=:]\\s*[^\\s,;&]+"),
                    "$1=[secret]"),
            new Regle(Pattern.compile("[\\w.%+-]+@[\\w-]+(?:\\.[\\w-]+)+"), "[courriel]"),
            new Regle(Pattern.compile("(?<![\\w-])(?:\\+?1[\\s.-]?)?\\(?\\d{3}\\)?[\\s.-]?\\d{3}[\\s.-]?\\d{4}(?![\\w-])"),
                    "[telephone]"));

    private MasqueRenseignements() {
    }

    public static String appliquer(String texte) {
        if (texte == null) {
            return null;
        }
        String resultat = texte;
        for (Regle regle : REGLES) {
            resultat = regle.motif().matcher(resultat).replaceAll(regle.remplacement());
        }
        return resultat;
    }
}
