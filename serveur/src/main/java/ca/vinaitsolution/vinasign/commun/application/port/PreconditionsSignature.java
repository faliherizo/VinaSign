package ca.vinaitsolution.vinasign.commun.application.port;

import ca.vinaitsolution.vinasign.commun.domain.IdentiteSignataire;

/** Ce qu'exige toute signature ou tout refus (consentement, niveau d'authentification) ; lève une erreur métier sinon. */
public interface PreconditionsSignature {

    void exiger(IdentiteSignataire signataire);
}
