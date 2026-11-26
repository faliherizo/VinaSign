package ca.vinaitsolution.vinasign.integration.application.port;

import ca.vinaitsolution.vinasign.integration.domain.DestinationWebhook;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Destinations de webhooks, sous la RLS de l'organisation. */
public interface DestinationsWebhook {

    void ajouter(DestinationWebhook destination, String secretChiffre);

    List<DestinationWebhook> actives(String client);

    Optional<DestinationWebhook> active(UUID id);

    void retirer(UUID id, Instant le);
}
