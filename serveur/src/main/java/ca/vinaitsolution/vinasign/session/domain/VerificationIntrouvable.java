package ca.vinaitsolution.vinasign.session.domain;

import ca.vinaitsolution.vinasign.commun.domain.ErreurMetier;

public class VerificationIntrouvable extends ErreurMetier {

    public VerificationIntrouvable() {
        super(Nature.INTROUVABLE, "verification-introuvable", "Vérification d'identité introuvable pour cette session.");
    }
}
