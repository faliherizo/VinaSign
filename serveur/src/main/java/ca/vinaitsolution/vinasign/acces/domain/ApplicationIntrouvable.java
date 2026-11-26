package ca.vinaitsolution.vinasign.acces.domain;

import ca.vinaitsolution.vinasign.commun.domain.ErreurMetier;

public class ApplicationIntrouvable extends ErreurMetier {

    public ApplicationIntrouvable() {
        super(Nature.INTROUVABLE, "application-introuvable", "Application cliente introuvable dans cette organisation.");
    }
}
