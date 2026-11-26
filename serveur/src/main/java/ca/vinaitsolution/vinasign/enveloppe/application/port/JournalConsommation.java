package ca.vinaitsolution.vinasign.enveloppe.application.port;

import ca.vinaitsolution.vinasign.enveloppe.domain.Consommation;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Événements d'enveloppe de l'organisation courante utiles à la mesure de consommation (VS-120). */
public interface JournalConsommation {

    List<Consommation.Evenement> entre(Instant debut, Instant fin);

    Set<UUID> envoyeesAvant(Instant debut);
}
