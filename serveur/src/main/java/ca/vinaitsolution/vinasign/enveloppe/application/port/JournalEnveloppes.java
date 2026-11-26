package ca.vinaitsolution.vinasign.enveloppe.application.port;

import ca.vinaitsolution.vinasign.enveloppe.domain.EvenementEnveloppe;
import ca.vinaitsolution.vinasign.enveloppe.domain.IdentifiantEnveloppe;
import java.util.List;

/** Journal des événements d'une enveloppe ; un type publié en webhook est aussi déposé dans la boîte d'envoi. */
public interface JournalEnveloppes {

    void consigner(EvenementEnveloppe evenement);

    List<EvenementEnveloppe> de(IdentifiantEnveloppe enveloppe);
}
