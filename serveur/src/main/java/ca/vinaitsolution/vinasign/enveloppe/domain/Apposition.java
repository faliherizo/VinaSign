package ca.vinaitsolution.vinasign.enveloppe.domain;

/**
 * Ce que la finalisation appose sur une page (VS-070) : dans le rectangle d'un champ (coordonnées normalisées, origine
 * en haut à gauche), soit l'image de la signature, soit un texte (valeur saisie, nom tapé).
 */
public record Apposition(int page, double x, double y, double largeur, double hauteur, String texte, byte[] image) {

    public Apposition {
        if (page < 1 || (texte == null) == (image == null)) {
            throw new IllegalArgumentException("Une apposition porte, sur une page, soit un texte, soit une image.");
        }
        image = image == null ? null : image.clone();
    }

    @Override
    public byte[] image() {
        return image == null ? null : image.clone();
    }

    public static Apposition texte(Champ c, String texte) {
        return new Apposition(c.page(), c.x(), c.y(), c.largeur(), c.hauteur(), texte, null);
    }

    public static Apposition image(Champ c, byte[] image) {
        return new Apposition(c.page(), c.x(), c.y(), c.largeur(), c.hauteur(), null, image);
    }
}
