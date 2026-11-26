package ca.vinaitsolution.vinasign.enveloppe.domain;

import ca.vinaitsolution.vinasign.commun.domain.IdentifiantOrganisation;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Fait survenu à une enveloppe, consigné dans son journal (et, si son type le prévoit, publié en webhook).
 *
 * @param type    code du référentiel {@code evenement-enveloppe}
 * @param donnees détails non personnels (identifiants opaques, codes)
 */
public record EvenementEnveloppe(UUID id, IdentifiantEnveloppe enveloppe, IdentifiantOrganisation organisation, String type,
                                 String acteur, Instant survenuLe, Map<String, String> donnees) {

    public EvenementEnveloppe {
        if (id == null || enveloppe == null || organisation == null || type == null || type.isBlank() || acteur == null
                || survenuLe == null) {
            throw new IllegalArgumentException("Événement d'enveloppe incomplet.");
        }
        donnees = donnees == null ? Map.of() : Map.copyOf(donnees);
    }
}
