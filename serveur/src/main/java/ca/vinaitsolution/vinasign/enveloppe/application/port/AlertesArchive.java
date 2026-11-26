package ca.vinaitsolution.vinasign.enveloppe.application.port;

import ca.vinaitsolution.vinasign.enveloppe.domain.IdentifiantEnveloppe;

/** Alerte d'exploitation (VS-132) : un renouvellement a échoué ; la tâche réessaiera, l'échéance approche. */
public interface AlertesArchive {

    void echec(IdentifiantEnveloppe enveloppe, RuntimeException cause);
}
