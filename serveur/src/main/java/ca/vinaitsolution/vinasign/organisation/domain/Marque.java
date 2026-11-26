package ca.vinaitsolution.vinasign.organisation.domain;

import ca.vinaitsolution.vinasign.commun.domain.ErreurMetier;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Marque d'une organisation (VS-123), affichée au signataire. La couleur principale sert de fond à un texte blanc (boutons) :
 * son contraste avec le blanc, calculé selon WCAG 2.2 (luminance relative), doit valoir au moins 4,5:1, sinon elle est refusée.
 */
public record Marque(String nomAffiche, String couleurPrimaire, byte[] logo, String sousDomaine) {

    public static final double CONTRASTE_MIN = 4.5;
    private static final Pattern COULEUR = Pattern.compile("#[0-9a-f]{6}");
    private static final Pattern SOUS_DOMAINE = Pattern.compile("[a-z0-9](?:[a-z0-9-]{1,38}[a-z0-9])");
    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G'};
    private static final int LOGO_MAX = 100_000;

    public Marque {
        nomAffiche = nomAffiche == null ? "" : nomAffiche.strip();
        if (nomAffiche.isEmpty() || nomAffiche.length() > 80) {
            throw new MarqueInvalide("Le nom affiché est requis (80 caractères au plus).");
        }
        couleurPrimaire = couleurPrimaire == null ? "" : couleurPrimaire.toLowerCase(Locale.ROOT);
        if (!COULEUR.matcher(couleurPrimaire).matches()) {
            throw new MarqueInvalide("Couleur attendue au format #rrggbb.");
        }
        double c = contraste(couleurPrimaire, "#ffffff");
        if (c < CONTRASTE_MIN) {
            throw new MarqueInvalide(String.format(Locale.ROOT, "Contraste insuffisant avec le texte blanc : %.2f:1, %.1f:1 exigé (WCAG 2.2 AA).", c, CONTRASTE_MIN));
        }
        if (logo != null && (logo.length > LOGO_MAX || logo.length < PNG.length || !java.util.Arrays.equals(java.util.Arrays.copyOf(logo, PNG.length), PNG))) {
            throw new MarqueInvalide("Logo attendu : image PNG de 100 Ko au plus.");
        }
        logo = logo == null ? null : logo.clone();
        if (sousDomaine != null && !SOUS_DOMAINE.matcher(sousDomaine).matches()) {
            throw new MarqueInvalide("Sous-domaine : 3 à 40 caractères, lettres minuscules, chiffres et traits d'union.");
        }
    }

    @Override
    public byte[] logo() {
        return logo == null ? null : logo.clone();
    }

    /** Rapport de contraste WCAG entre deux couleurs #rrggbb, de 1 à 21. */
    public static double contraste(String a, String b) {
        double la = luminance(a);
        double lb = luminance(b);
        return (Math.max(la, lb) + 0.05) / (Math.min(la, lb) + 0.05);
    }

    private static double luminance(String hex) {
        double[] k = {0.2126, 0.7152, 0.0722};
        double l = 0;
        for (int i = 0; i < 3; i++) {
            double c = Integer.parseInt(hex.substring(1 + 2 * i, 3 + 2 * i), 16) / 255.0;
            l += k[i] * (c <= 0.04045 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4));
        }
        return l;
    }

    /** Marque refusée ; le message dit quoi corriger. */
    public static class MarqueInvalide extends ErreurMetier {

        public MarqueInvalide(String detail) {
            super(Nature.INVALIDE, "marque-invalide", detail);
        }
    }
}
