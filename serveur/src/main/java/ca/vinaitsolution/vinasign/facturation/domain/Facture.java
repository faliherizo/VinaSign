package ca.vinaitsolution.vinasign.facturation.domain;

import java.time.Instant;
import java.util.UUID;

/** Facture payée, émise et hébergée par le prestataire (VS-121) : montant en plus petite unité de la devise, liens hébergés. */
public record Facture(UUID id, UUID organisation, String reference, String numero, long montant, String devise, String urlHebergee, String urlPdf,
                      Instant debutPeriode, Instant finPeriode, Instant payeeLe) {

    public Facture {
        if (id == null || organisation == null || reference == null || devise == null || payeeLe == null) {
            throw new IllegalArgumentException("Facture incomplète.");
        }
        if (montant < 0) {
            throw new IllegalArgumentException("Montant négatif.");
        }
    }
}
