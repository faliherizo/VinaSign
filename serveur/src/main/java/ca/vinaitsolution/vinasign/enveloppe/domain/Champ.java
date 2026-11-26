package ca.vinaitsolution.vinasign.enveloppe.domain;

import java.util.UUID;

/**
 * Champ à remplir par un signataire (VS-052) : type (référentiel {@code type-champ}), page et rectangle en coordonnées
 * normalisées (0 à 1, origine en haut à gauche), indépendantes du zoom et de la résolution d'affichage (VS-060).
 *
 * @param libelle libellé affiché, qui dit au signataire quoi saisir
 */
public record Champ(UUID id, UUID signataire, String type, String libelle, int page, double x, double y, double largeur, double hauteur,
                    boolean obligatoire) {

    public Champ {
        if (id == null || signataire == null || type == null || type.isBlank()) {
            throw new IllegalArgumentException("Champ incomplet.");
        }
        libelle = libelle == null ? "" : libelle.strip();
        if (libelle.isEmpty() || libelle.length() > 120) {
            throw new IllegalArgumentException("Le libellé du champ est requis (120 caractères au plus).");
        }
        if (page < 1) {
            throw new IllegalArgumentException("La page commence à 1.");
        }
        if (!dansLaPage(x, largeur) || !dansLaPage(y, hauteur)) {
            throw new IllegalArgumentException("Le champ doit tenir dans la page (coordonnées normalisées de 0 à 1).");
        }
    }

    private static boolean dansLaPage(double origine, double taille) {
        return origine >= 0 && taille > 0 && origine + taille <= 1.0 + 1e-9;
    }
}
