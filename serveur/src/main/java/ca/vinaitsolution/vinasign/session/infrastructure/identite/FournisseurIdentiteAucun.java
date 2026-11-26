package ca.vinaitsolution.vinasign.session.infrastructure.identite;

import ca.vinaitsolution.vinasign.session.application.port.FournisseurIdentite;
import ca.vinaitsolution.vinasign.session.domain.VerificationIdentiteIndisponible;
import ca.vinaitsolution.vinasign.session.domain.VerificationIdentiteRefusee;

/** Aucun fournisseur configuré : la vérification n'est pas offerte, et aucun rappel n'est accepté. */
public class FournisseurIdentiteAucun implements FournisseurIdentite {

    @Override
    public String code() {
        return "aucun";
    }

    @Override
    public String demarrer(Demande demande) {
        throw new VerificationIdentiteIndisponible();
    }

    @Override
    public Conclusion conclure(byte[] corps, String signature) {
        throw new VerificationIdentiteRefusee("Aucun fournisseur de vérification d'identité configuré.");
    }
}
