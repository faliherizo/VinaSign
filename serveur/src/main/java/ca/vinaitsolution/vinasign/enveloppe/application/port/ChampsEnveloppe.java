package ca.vinaitsolution.vinasign.enveloppe.application.port;

import ca.vinaitsolution.vinasign.enveloppe.domain.Champ;
import ca.vinaitsolution.vinasign.enveloppe.domain.IdentifiantEnveloppe;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Champs des enveloppes et valeurs saisies par les signataires. */
public interface ChampsEnveloppe {

    /** Remplace les champs de l'enveloppe (en brouillon). */
    void definir(IdentifiantEnveloppe enveloppe, List<Champ> champs);

    List<Champ> de(IdentifiantEnveloppe enveloppe);

    void enregistrerValeurs(IdentifiantEnveloppe enveloppe, UUID signataire, Map<UUID, String> valeurs);
}
