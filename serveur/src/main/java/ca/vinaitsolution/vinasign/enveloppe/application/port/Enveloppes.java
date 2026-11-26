package ca.vinaitsolution.vinasign.enveloppe.application.port;

import ca.vinaitsolution.vinasign.commun.domain.IdentifiantOrganisation;
import ca.vinaitsolution.vinasign.enveloppe.domain.Enveloppe;
import ca.vinaitsolution.vinasign.enveloppe.domain.IdentifiantEnveloppe;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** Enveloppes de l'organisation courante. */
public interface Enveloppes {

    /** {@code ReferenceDejaUtilisee} si l'application a déjà cette référence externe. */
    void ajouter(Enveloppe enveloppe);

    void remplacer(Enveloppe enveloppe);

    Optional<Enveloppe> parId(IdentifiantEnveloppe id);

    /** Enveloppes créées par l'organisation depuis cet instant (quota mensuel). */
    long creeesDepuis(IdentifiantOrganisation organisation, Instant debut);
}
