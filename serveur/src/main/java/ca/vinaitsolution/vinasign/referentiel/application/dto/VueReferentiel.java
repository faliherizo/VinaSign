package ca.vinaitsolution.vinasign.referentiel.application.dto;

import java.util.List;

/** Référentiel et ses valeurs actives, libellés dans la langue demandée. */
public record VueReferentiel(String code, String libelle, String langue, List<VueValeur> valeurs) {
}
