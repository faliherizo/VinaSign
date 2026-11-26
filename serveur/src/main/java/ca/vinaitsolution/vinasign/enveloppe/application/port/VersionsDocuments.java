package ca.vinaitsolution.vinasign.enveloppe.application.port;

import ca.vinaitsolution.vinasign.enveloppe.domain.IdentifiantEnveloppe;
import ca.vinaitsolution.vinasign.enveloppe.domain.VersionDocument;
import java.util.List;

/** Versions immuables : ajout seul ; une seule version par nature et par enveloppe. */
public interface VersionsDocuments {

    /** {@code VersionImmuable} si l'enveloppe a déjà une version de cette nature. */
    void ajouter(VersionDocument version);

    List<VersionDocument> de(IdentifiantEnveloppe enveloppe);
}
