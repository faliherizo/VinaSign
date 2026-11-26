package ca.vinaitsolution.vinasign.commun.application.port;

import ca.vinaitsolution.vinasign.commun.domain.IdentifiantOrganisation;

/** Changement du plan d'une organisation par la facturation (VS-121), sans accès aux entités du contexte organisation. */
public interface SouscriptionPlans {

    void appliquer(IdentifiantOrganisation organisation, String plan);

    void revenirAuPlanParDefaut(IdentifiantOrganisation organisation);
}
