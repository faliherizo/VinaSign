package ca.vinaitsolution.vinasign.session.domain;

import java.time.Duration;
import java.time.Instant;

/**
 * Relances des signataires (VS-082), portées de {@code Relances} du CLM : fonctions pures de l'horloge. Une relance est
 * due quand la fréquence est écoulée depuis le dernier envoi (invitation ou relance), dans la limite permise, elle-même
 * bornée par un plafond.
 */
public final class Relances {

    public static final int PLAFOND = 10;

    private Relances() {
    }

    public static boolean relanceDue(Instant dernierEnvoi, int relancesEnvoyees, Instant maintenant, Duration frequence, int relancesMax) {
        if (dernierEnvoi == null || frequence.isNegative() || frequence.isZero() || relancesEnvoyees >= Math.min(relancesMax, PLAFOND)) {
            return false;
        }
        return !maintenant.isBefore(dernierEnvoi.plus(frequence));
    }

    /** L'échéance approche : elle tombe dans le préavis et n'est pas dépassée. Un préavis nul n'alerte jamais. */
    public static boolean echeanceProche(Instant echeance, Instant maintenant, Duration preavis) {
        return echeance != null && !preavis.isZero() && !preavis.isNegative() && !maintenant.isAfter(echeance)
                && !maintenant.isBefore(echeance.minus(preavis));
    }
}
