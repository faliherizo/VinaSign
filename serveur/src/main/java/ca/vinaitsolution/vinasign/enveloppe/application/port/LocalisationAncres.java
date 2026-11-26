package ca.vinaitsolution.vinasign.enveloppe.application.port;

import java.util.List;

/** Recherche d'un texte d'ancre dans le PDF (VS-063) : chaque occurrence, en coordonnées normalisées (0 à 1, haut-gauche). */
public interface LocalisationAncres {

    record Emplacement(int page, double x, double y, double largeur, double hauteur) {
    }

    List<Emplacement> localiser(byte[] pdf, String ancre);
}
