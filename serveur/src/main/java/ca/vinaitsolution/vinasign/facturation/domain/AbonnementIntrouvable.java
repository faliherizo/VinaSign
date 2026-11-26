package ca.vinaitsolution.vinasign.facturation.domain;

import ca.vinaitsolution.vinasign.commun.domain.ErreurMetier;

public class AbonnementIntrouvable extends ErreurMetier {

    public AbonnementIntrouvable() {
        super(Nature.INTROUVABLE, "abonnement-introuvable", "Aucun abonnement payé pour cette organisation.");
    }
}
