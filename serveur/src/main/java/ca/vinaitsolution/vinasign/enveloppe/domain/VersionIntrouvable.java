package ca.vinaitsolution.vinasign.enveloppe.domain;

import ca.vinaitsolution.vinasign.commun.domain.ErreurMetier;

/** L'enveloppe n'a pas (encore) de version de cette nature, par exemple de version finale avant la finalisation. */
public class VersionIntrouvable extends ErreurMetier {

    public VersionIntrouvable() {
        super(Nature.INTROUVABLE, "version-introuvable", "Cette enveloppe n'a pas de version de document de cette nature.");
    }
}
