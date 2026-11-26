package ca.vinaitsolution.vinasign.etat.domain;

import ca.vinaitsolution.vinasign.commun.domain.ErreurMetier;

/** Incident refusé ; le message dit quoi corriger. */
public class IncidentInvalide extends ErreurMetier {

    public IncidentInvalide(String detail) {
        super(Nature.INVALIDE, "incident-invalide", detail);
    }
}
