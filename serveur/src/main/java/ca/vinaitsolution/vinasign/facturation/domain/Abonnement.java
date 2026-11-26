package ca.vinaitsolution.vinasign.facturation.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * Abonnement d'une organisation chez le prestataire de paiement (VS-121) : identifiants du prestataire, plan payé, état.
 * Le plan de l'organisation suit l'abonnement : celui payé quand il est actif, celui par défaut quand il est terminé ; un
 * impayé laisse le plan en place, le temps des relances du prestataire.
 */
public record Abonnement(UUID organisation, String prestataire, String client, String reference, String plan, EtatAbonnement etat, Instant modifieLe) {

    public Abonnement {
        if (organisation == null || prestataire == null || client == null || plan == null || etat == null || modifieLe == null) {
            throw new IllegalArgumentException("Abonnement incomplet.");
        }
    }

    /** Ce que le changement d'état impose au plan de l'organisation. */
    public enum EffetPlan { APPLIQUER, PAR_DEFAUT, AUCUN }

    public EffetPlan effetPlan() {
        return switch (etat) {
            case ACTIF -> EffetPlan.APPLIQUER;
            case TERMINE -> EffetPlan.PAR_DEFAUT;
            case IMPAYE -> EffetPlan.AUCUN;
        };
    }

    /** Nouvel état ou nouveau plan annoncé par le prestataire ; un plan inconnu de la configuration garde le plan actuel. */
    public Abonnement evoluer(String reference, String plan, EtatAbonnement etat, Instant le) {
        return new Abonnement(organisation, prestataire, client, reference == null ? this.reference : reference, plan == null ? this.plan : plan, etat, le);
    }
}
