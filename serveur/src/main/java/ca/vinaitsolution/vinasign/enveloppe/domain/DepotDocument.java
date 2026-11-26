package ca.vinaitsolution.vinasign.enveloppe.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * Dépôt d'un document par URL présignée (VS-021) : un jeton de 256 bits (seule son empreinte est conservée), valable
 * jusqu'à {@code expireLe}, pour un seul objet. {@code recuLe} : objet reçu ; {@code confirmeLe} : contrôlé et retenu.
 */
public record DepotDocument(UUID id, IdentifiantEnveloppe enveloppe, String empreinteJeton, String cleObjet, Instant expireLe,
                            Instant recuLe, Instant confirmeLe) {

    public DepotDocument {
        if (id == null || enveloppe == null || empreinteJeton == null || cleObjet == null || expireLe == null) {
            throw new IllegalArgumentException("Dépôt incomplet.");
        }
    }

    public boolean accepteUnObjet(Instant maintenant) {
        return recuLe == null && maintenant.isBefore(expireLe);
    }
}
