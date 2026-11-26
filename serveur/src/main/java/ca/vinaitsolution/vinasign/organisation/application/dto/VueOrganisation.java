package ca.vinaitsolution.vinasign.organisation.application.dto;

import ca.vinaitsolution.vinasign.organisation.domain.Organisation;
import java.time.Instant;
import java.util.UUID;

/**
 * @param statut code du référentiel {@code statut-organisation}
 * @param plan   code du référentiel {@code plan}
 */
public record VueOrganisation(UUID id, String nom, String statut, String plan, Instant creeLe, Instant modifieLe) {

    public static VueOrganisation de(Organisation o) {
        return new VueOrganisation(o.id().valeur(), o.nom(), o.statut().code(), o.plan(), o.creeLe(), o.modifieLe());
    }
}
