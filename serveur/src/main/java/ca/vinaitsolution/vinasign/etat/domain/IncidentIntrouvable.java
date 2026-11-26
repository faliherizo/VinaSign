package ca.vinaitsolution.vinasign.etat.domain;

import ca.vinaitsolution.vinasign.commun.domain.ErreurMetier;

public class IncidentIntrouvable extends ErreurMetier {

    public IncidentIntrouvable() {
        super(Nature.INTROUVABLE, "incident-introuvable", "Incident introuvable.");
    }
}
