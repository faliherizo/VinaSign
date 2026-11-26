package ca.vinaitsolution.vinasign.enveloppe.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * Ancrage horaire du journal (VS-071) : l'empreinte agrégée des événements consignés dans l'intervalle ]depuis, jusqua],
 * toutes organisations confondues, et sa signature CMS par le cachet de la plateforme (horodatée si une autorité est
 * configurée).
 */
public record Ancrage(UUID id, Instant depuis, Instant jusqua, int nombre, String empreinteAgregee, byte[] signature, Instant ancreLe) {

    public Ancrage {
        if (id == null || depuis == null || jusqua == null || !jusqua.isAfter(depuis) || nombre < 0 || empreinteAgregee == null
                || signature == null || ancreLe == null) {
            throw new IllegalArgumentException("Ancrage incomplet.");
        }
        signature = signature.clone();
    }

    @Override
    public byte[] signature() {
        return signature.clone();
    }

}
