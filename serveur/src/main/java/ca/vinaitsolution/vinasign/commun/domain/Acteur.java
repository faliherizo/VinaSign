package ca.vinaitsolution.vinasign.commun.domain;

/**
 * Auteur authentifié d'une action : sujet du jeton (application cliente ou utilisateur du portail). Jamais un courriel :
 * la référence est un identifiant opaque, consignable sans exposer de renseignement personnel.
 */
public record Acteur(String reference) {

    public Acteur {
        if (reference == null || reference.isBlank() || reference.length() > 200) {
            throw new IllegalArgumentException("Référence d'acteur invalide.");
        }
    }
}
