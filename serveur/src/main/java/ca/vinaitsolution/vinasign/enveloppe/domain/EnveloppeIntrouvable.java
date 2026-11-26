package ca.vinaitsolution.vinasign.enveloppe.domain;

import ca.vinaitsolution.vinasign.commun.domain.ErreurMetier;

public class EnveloppeIntrouvable extends ErreurMetier {

    public EnveloppeIntrouvable() {
        super(Nature.INTROUVABLE, "enveloppe-introuvable", "Enveloppe introuvable.");
    }
}
