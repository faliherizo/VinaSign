package ca.vinaitsolution.vinasign.enveloppe.domain;

import ca.vinaitsolution.vinasign.commun.domain.ErreurMetier;

/** Une version de document ne se réécrit jamais (VS-022). */
public class VersionImmuable extends ErreurMetier {

    public VersionImmuable() {
        super(Nature.CONFLIT, "version-immuable", "Cette version existe déjà et ne se réécrit pas.");
    }
}
