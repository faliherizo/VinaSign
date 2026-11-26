package ca.vinaitsolution.vinasign.organisation.domain;

import ca.vinaitsolution.vinasign.commun.domain.IdentifiantOrganisation;
import java.time.Instant;

/**
 * Organisation cliente de la plateforme (VS-010). Elle ne se suspend qu'active et ne se réactive que suspendue : une
 * transition répétée est un conflit, pas une opération sans effet, pour que l'audit ne consigne que des changements.
 */
/**
 * @param plan code du référentiel {@code plan} : débit et quotas de l'organisation (VS-015)
 */
public record Organisation(IdentifiantOrganisation id, String nom, StatutOrganisation statut, Instant creeLe, Instant modifieLe,
                           String plan) {

    private static final int NOM_MAX = 200;

    public Organisation {
        if (id == null || statut == null || creeLe == null || modifieLe == null) {
            throw new IllegalArgumentException("Organisation incomplète.");
        }
        nom = nom == null ? null : nom.strip();
        if (nom == null || nom.isEmpty() || nom.length() > NOM_MAX) {
            throw new IllegalArgumentException("Le nom de l'organisation est requis (200 caractères au plus).");
        }
        if (plan == null || plan.isBlank()) {
            throw new IllegalArgumentException("Une organisation a un plan.");
        }
    }

    public static Organisation creer(IdentifiantOrganisation id, String nom, String plan, Instant le) {
        return new Organisation(id, nom, StatutOrganisation.ACTIVE, le, le, plan);
    }

    public boolean estActive() {
        return statut == StatutOrganisation.ACTIVE;
    }

    public Organisation suspendre(Instant le) {
        if (!estActive()) {
            throw new TransitionOrganisationInterdite("L'organisation est déjà suspendue.");
        }
        return new Organisation(id, nom, StatutOrganisation.SUSPENDUE, creeLe, le, plan);
    }

    public Organisation reactiver(Instant le) {
        if (estActive()) {
            throw new TransitionOrganisationInterdite("L'organisation est déjà active.");
        }
        return new Organisation(id, nom, StatutOrganisation.ACTIVE, creeLe, le, plan);
    }

    /** Changement de plan : immédiat, consigné par le cas d'utilisation. */
    public Organisation changerPlan(String nouveau, Instant le) {
        return new Organisation(id, nom, statut, creeLe, le, nouveau);
    }
}
