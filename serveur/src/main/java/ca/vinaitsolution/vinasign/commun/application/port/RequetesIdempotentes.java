package ca.vinaitsolution.vinasign.commun.application.port;

import ca.vinaitsolution.vinasign.commun.domain.IssueReservation;
import java.time.Instant;

/** Mémoire des requêtes idempotentes de l'organisation courante (VS-020). */
public interface RequetesIdempotentes {

    /** Réservation : pour REJOUEE, le statut et le corps mémorisés. */
    record Reservation(IssueReservation etat, int statut, String reponse) {
    }

    /**
     * Réserve la clé de ce client pour cette empreinte de corps, jusqu'à {@code expireLe} ; une clé expirée se réserve à
     * nouveau.
     */
    Reservation reserver(String client, String cle, String empreinte, Instant maintenant, Instant expireLe);

    void memoriser(String client, String cle, int statut, String reponse);
}
