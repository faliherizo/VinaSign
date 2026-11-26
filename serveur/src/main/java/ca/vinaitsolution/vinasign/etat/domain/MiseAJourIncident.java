package ca.vinaitsolution.vinasign.etat.domain;

import ca.vinaitsolution.vinasign.commun.domain.Libelles;
import java.time.Instant;
import java.util.UUID;

/** Communication publiée sur un incident : phase (référentiel phase-incident) et message par langue. */
public record MiseAJourIncident(UUID id, String phase, Libelles message, Instant le) {

    public MiseAJourIncident {
        if (id == null || phase == null || phase.isBlank() || le == null) {
            throw new IllegalArgumentException("Mise à jour incomplète.");
        }
        Incident.exigerTexte(message, 2000, "Le message");
    }
}
