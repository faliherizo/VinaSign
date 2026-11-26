package ca.vinaitsolution.vinasign.referentiel.application.dto;

import java.util.Map;

/** Valeur proposable, libellée dans la langue demandée : de quoi remplir une liste de l'interface. */
public record VueValeur(String code, String libelle, int ordre, Map<String, String> attributs) {
}
