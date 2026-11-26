package ca.vinaitsolution.vinasign.referentiel.infrastructure.web;

import java.util.Locale;
import java.util.Optional;

/**
 * Langue préférée d'après l'en-tête Accept-Language (RFC 9110, § 12.5.4). Un navigateur envoie une liste pondérée
 * (« fr-CA,fr;q=0.9,en;q=0.8 »), pas une seule balise : la convertir d'un bloc en Locale échoue (400). On retient la
 * langue de plus fort poids (à poids égal, la première de l'en-tête) ; un en-tête absent, joker ou mal formé ne donne
 * aucune préférence, et c'est alors la langue principale configurée qui s'applique.
 */
final class PreferenceLangue {

    private PreferenceLangue() {
    }

    static Optional<String> depuis(String enTete) {
        if (enTete == null || enTete.isBlank()) {
            return Optional.empty();
        }
        try {
            // parse() rend les plages triées par poids décroissant, dans l'ordre de l'en-tête à poids égal.
            return Locale.LanguageRange.parse(enTete).stream()
                    .filter(p -> p.getWeight() > 0 && !p.getRange().startsWith("*"))
                    .map(p -> Locale.forLanguageTag(p.getRange()).getLanguage())
                    .filter(langue -> !langue.isBlank())
                    .findFirst();
        } catch (IllegalArgumentException malForme) {
            return Optional.empty();
        }
    }
}
