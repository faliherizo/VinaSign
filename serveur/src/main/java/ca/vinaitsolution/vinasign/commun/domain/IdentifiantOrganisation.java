package ca.vinaitsolution.vinasign.commun.domain;

import java.util.UUID;

/** Organisation cliente (tenant) : ce que toute donnée métier porte et ce que la base filtre (VS-ADR-006). */
public record IdentifiantOrganisation(UUID valeur) {

    public IdentifiantOrganisation {
        if (valeur == null) {
            throw new IllegalArgumentException("Une organisation a un identifiant.");
        }
    }

    /** Lecture d'un identifiant transmis (jeton, configuration) ; un texte mal formé est refusé. */
    public static IdentifiantOrganisation depuis(String texte) {
        try {
            return new IdentifiantOrganisation(UUID.fromString(texte == null ? "" : texte.strip()));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Identifiant d'organisation invalide.", e);
        }
    }
}
