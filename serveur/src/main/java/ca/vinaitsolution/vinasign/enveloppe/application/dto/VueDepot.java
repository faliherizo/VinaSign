package ca.vinaitsolution.vinasign.enveloppe.application.dto;

import java.time.Instant;
import java.util.UUID;

/** URL présignée de dépôt : un seul objet, avant {@code expireLe} ; à confirmer ensuite par {@code id}. */
public record VueDepot(UUID id, String url, Instant expireLe) {
}
