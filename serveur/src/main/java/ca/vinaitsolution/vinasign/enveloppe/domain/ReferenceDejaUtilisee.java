package ca.vinaitsolution.vinasign.enveloppe.domain;

import ca.vinaitsolution.vinasign.commun.domain.ErreurMetier;

public class ReferenceDejaUtilisee extends ErreurMetier {

    public ReferenceDejaUtilisee() {
        super(Nature.CONFLIT, "reference-externe-deja-utilisee", "Cette application a déjà une enveloppe portant cette référence externe.");
    }
}
