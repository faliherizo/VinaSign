package ca.vinaitsolution.vinasign.session.domain;

import ca.vinaitsolution.vinasign.commun.domain.ErreurMetier;

/** Une clé d'accès ne s'enregistre qu'une fois le signataire authentifié autrement (code ou attestation de l'application). */
public class AuthentificationPrealableRequise extends ErreurMetier {

    public AuthentificationPrealableRequise() {
        super(Nature.INTERDIT, "authentification-prealable-requise",
                "Vérifier d'abord son identité par un code avant d'enregistrer une clé d'accès.");
    }
}
