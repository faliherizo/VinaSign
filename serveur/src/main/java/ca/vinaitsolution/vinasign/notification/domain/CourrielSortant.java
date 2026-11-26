package ca.vinaitsolution.vinasign.notification.domain;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * Courriel dans la boîte d'envoi (VS-080) : écrit dans la transaction de l'action qui le cause, envoyé ensuite, repris
 * en cas d'échec avec un délai croissant, abandonné au-delà du plafond. Aucune pièce jointe.
 */
public record CourrielSortant(UUID id, UUID organisation, String destinataire, String sujet, String corps, int tentatives,
                              Instant prochaineTentative, UUID enveloppe) {

    /** Courriel sans enveloppe de rattachement. */
    public CourrielSortant(UUID id, UUID organisation, String destinataire, String sujet, String corps, int tentatives, Instant prochaineTentative) {
        this(id, organisation, destinataire, sujet, corps, tentatives, prochaineTentative, null);
    }

    public CourrielSortant {
        if (id == null || organisation == null || destinataire == null || destinataire.isBlank() || sujet == null || corps == null
                || prochaineTentative == null || tentatives < 0) {
            throw new IllegalArgumentException("Courriel incomplet.");
        }
    }

    /** Délai avant la tentative suivante : 1, 2, 4, 8… minutes, plafonné à une heure. */
    public Instant prochaineApresEchec(Instant maintenant) {
        long minutes = Math.min(60, 1L << Math.min(tentatives, 6));
        return maintenant.plus(Duration.ofMinutes(minutes));
    }

    /** Sans le destinataire ni le corps (qui porte le lien) : rien de personnel ni de secret dans une trace. */
    @Override
    public String toString() {
        return "CourrielSortant[" + id + ", tentatives=" + tentatives + "]";
    }
}
