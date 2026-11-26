package ca.vinaitsolution.vinasign.session.domain;

import ca.vinaitsolution.vinasign.commun.domain.ErreurMetier;

/** Le signataire doit atteindre le niveau d'authentification exigé (code ou clé d'accès) avant de signer (VS-042, VS-043). */
public class AuthentificationRequise extends ErreurMetier {

    public AuthentificationRequise(String niveau) {
        super(Nature.INTERDIT, "authentification-requise", "Authentification de niveau « " + niveau + " » requise avant de signer : code ou clé d'accès.");
    }
}
