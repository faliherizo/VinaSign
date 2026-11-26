package ca.vinaitsolution.vinasign.etat.application.port;

import ca.vinaitsolution.vinasign.etat.domain.Incident;
import ca.vinaitsolution.vinasign.etat.domain.MiseAJourIncident;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Incidents de la plateforme (données publiques, sans organisation). Les communications s'ajoutent, ne se modifient pas. */
public interface Incidents {

    void ajouter(Incident incident);

    /** Ajoute la communication et, si elle le résout, la fin de l'incident. */
    void publier(UUID incident, MiseAJourIncident miseAJour, Instant fin);

    Optional<Incident> parId(UUID id);

    /** Incidents ouverts, et ceux résolus depuis cet instant, du plus récent au plus ancien. */
    List<Incident> depuis(Instant instant);
}
