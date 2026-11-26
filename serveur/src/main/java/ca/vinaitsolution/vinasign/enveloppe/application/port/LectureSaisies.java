package ca.vinaitsolution.vinasign.enveloppe.application.port;

import ca.vinaitsolution.vinasign.enveloppe.domain.IdentifiantEnveloppe;
import ca.vinaitsolution.vinasign.enveloppe.domain.SignatureApposee;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Lecture de ce que les signataires ont saisi et signé, pour composer le document final (VS-070). */
public interface LectureSaisies {

    /** Valeurs saisies, par identifiant de champ. */
    Map<UUID, String> valeurs(IdentifiantEnveloppe enveloppe);

    List<SignatureApposee> signatures(IdentifiantEnveloppe enveloppe);
}
