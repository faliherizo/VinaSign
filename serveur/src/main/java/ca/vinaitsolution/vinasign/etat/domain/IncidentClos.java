package ca.vinaitsolution.vinasign.etat.domain;

import ca.vinaitsolution.vinasign.commun.domain.ErreurMetier;

/** Un incident résolu ne reçoit plus de mise à jour : en ouvrir un nouveau. */
public class IncidentClos extends ErreurMetier {

    public IncidentClos() {
        super(Nature.CONFLIT, "incident-clos", "Cet incident est résolu ; publier un nouvel incident.");
    }
}
