package ca.vinaitsolution.vinasign.enveloppe.domain;

import java.util.UUID;

public record IdentifiantEnveloppe(UUID valeur) {

    public IdentifiantEnveloppe {
        if (valeur == null) {
            throw new IllegalArgumentException("Une enveloppe a un identifiant.");
        }
    }
}
