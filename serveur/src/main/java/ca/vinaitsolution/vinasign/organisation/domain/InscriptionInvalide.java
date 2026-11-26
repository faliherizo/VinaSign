package ca.vinaitsolution.vinasign.organisation.domain;

import ca.vinaitsolution.vinasign.commun.domain.ErreurMetier;

/** Demande d'inscription refusée à cette étape ; le message dit quoi corriger (VS-122). */
public class InscriptionInvalide extends ErreurMetier {

    public InscriptionInvalide(String detail) {
        super(Nature.INVALIDE, "inscription-invalide", detail);
    }
}
