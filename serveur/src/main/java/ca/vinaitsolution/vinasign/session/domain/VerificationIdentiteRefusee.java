package ca.vinaitsolution.vinasign.session.domain;

import ca.vinaitsolution.vinasign.commun.domain.ErreurMetier;

/** Rappel du fournisseur d'identité refusé : signature invalide, référence inconnue ou résultat contradictoire (VS-131). */
public class VerificationIdentiteRefusee extends ErreurMetier {

    public VerificationIdentiteRefusee(String detail) {
        super(Nature.INTERDIT, "verification-identite-refusee", detail);
    }
}
