package ca.vinaitsolution.vinasign.notification.application.port;

import ca.vinaitsolution.vinasign.notification.domain.CourrielSortant;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Boîte d'envoi des courriels. */
public interface BoiteEnvoi {

    /** Dépôt dans l'organisation courante, dans la transaction de l'appelant. */
    void deposer(CourrielSortant courriel);

    /** Courriels dus, toutes organisations confondues (tâche d'expédition), réservés contre une expédition concurrente. */
    List<CourrielSortant> dus(Instant maintenant, int limite);

    /** Envoyé : le corps (qui porte le lien) est effacé. */
    void envoye(UUID id, Instant le);

    /** Échec : nouvelle tentative à cette date, ou abandon au-delà du plafond. */
    void echec(UUID id, int tentatives, Instant prochaine, boolean abandonne);
}
