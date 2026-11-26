package ca.vinaitsolution.vinasign.session.domain;

import ca.vinaitsolution.vinasign.commun.domain.ErreurMetier;

/** Cérémonie WebAuthn refusée (signature, origine, défi, compteur) ; le signataire peut se rabattre sur le code. */
public class CleAccesRefusee extends ErreurMetier {

    public CleAccesRefusee(String detail) {
        super(Nature.INTERDIT, "cle-acces-refusee", detail);
    }
}
