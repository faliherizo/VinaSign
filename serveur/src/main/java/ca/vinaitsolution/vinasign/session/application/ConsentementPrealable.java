package ca.vinaitsolution.vinasign.session.application;

import ca.vinaitsolution.vinasign.commun.application.port.PreconditionsSignature;
import ca.vinaitsolution.vinasign.commun.domain.IdentiteSignataire;

/** Aucune signature ni aucun refus sans consentement à la version en vigueur (VS-041). */
public class ConsentementPrealable implements PreconditionsSignature {

    private final Consentir consentir;

    public ConsentementPrealable(Consentir consentir) {
        this.consentir = consentir;
    }

    @Override
    public void exiger(IdentiteSignataire s) {
        consentir.exiger(s.enveloppe(), s.signataire());
    }
}
