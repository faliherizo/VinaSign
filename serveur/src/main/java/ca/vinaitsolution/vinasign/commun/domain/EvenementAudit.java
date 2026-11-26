package ca.vinaitsolution.vinasign.commun.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * Entrée du journal d'audit : qui a fait quoi, sur quoi, quand, pour quelle organisation.
 *
 * @param action code du référentiel {@code action-audit}
 * @param cible  identifiant opaque de l'objet visé
 */
public record EvenementAudit(UUID id, IdentifiantOrganisation organisation, Acteur acteur, String action, String cible,
                             Instant survenuLe) {

    public EvenementAudit {
        if (id == null || organisation == null || acteur == null || action == null || action.isBlank() || survenuLe == null) {
            throw new IllegalArgumentException("Événement d'audit incomplet.");
        }
    }
}
