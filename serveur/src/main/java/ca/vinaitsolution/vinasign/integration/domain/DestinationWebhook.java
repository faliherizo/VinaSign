package ca.vinaitsolution.vinasign.integration.domain;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/**
 * Destination des webhooks d'une application cliente (VS-091) : URL déclarée par elle, événements voulus (vide : tous).
 * Le secret de signature n'en fait pas partie : il est remis une fois, à la création, puis conservé chiffré.
 */
public record DestinationWebhook(UUID id, UUID organisation, String client, String url, Set<String> evenements, Instant creeLe) {

    private static final int URL_MAX = 2000;

    public DestinationWebhook {
        if (id == null || organisation == null || client == null || creeLe == null) {
            throw new IllegalArgumentException("Destination de webhook incomplète.");
        }
        if (url == null || url.isBlank() || url.length() > URL_MAX) {
            throw new DestinationInvalide("L'URL de destination est requise (" + URL_MAX + " caractères au plus).");
        }
        evenements = evenements == null ? Set.of() : Set.copyOf(evenements);
    }
}
