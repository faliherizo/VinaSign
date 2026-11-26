package ca.vinaitsolution.vinasign.enveloppe.application.port;

import ca.vinaitsolution.vinasign.enveloppe.domain.IdentifiantEnveloppe;
import java.time.Instant;
import java.util.UUID;

/** Trace de chaque signature apposée : mode (dessin, saisie), image ou nom saisi, heure. Aucune donnée de tracé. */
public interface SignaturesSignataires {

    /** @param cleImage clé de l'image dans le stockage (dessin) ; {@code nomSaisi} : alternative au clavier */
    void ajouter(UUID id, IdentifiantEnveloppe enveloppe, UUID signataire, String mode, String cleImage, String nomSaisi, Instant le);
}
