package ca.vinaitsolution.vinasign.enveloppe.application.port;

import ca.vinaitsolution.vinasign.enveloppe.domain.DepotDocument;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** Dépôts par URL présignée. */
public interface DepotsDocuments {

    void ajouter(DepotDocument depot);

    /**
     * Marque reçu le dépôt de ce jeton s'il accepte encore un objet (non expiré, rien reçu), hors de toute organisation
     * courante (l'appelant n'est authentifié que par le jeton) ; vide sinon.
     */
    Optional<DepotDocument> recevoir(String empreinteJeton, Instant maintenant);

    Optional<DepotDocument> parId(UUID id);

    void confirmer(UUID id, Instant le);
}
