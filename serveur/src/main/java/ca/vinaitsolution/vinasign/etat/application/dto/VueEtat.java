package ca.vinaitsolution.vinasign.etat.application.dto;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Page d'état (VS-124) : impact global courant, impact de chaque composant, incidents ouverts et historique. Les codes
 * (impact, composant, phase) se traduisent par les référentiels publics ; les textes sont rendus dans toutes leurs langues.
 */
public record VueEtat(String impact, Map<String, String> composants, List<VueIncident> incidents, Instant le) {

    public record VueIncident(UUID id, Map<String, String> titre, String impact, List<String> composants, Instant debut, Instant fin,
                              List<VueMiseAJour> misesAJour) {
    }

    public record VueMiseAJour(String phase, Map<String, String> message, Instant le) {
    }
}
