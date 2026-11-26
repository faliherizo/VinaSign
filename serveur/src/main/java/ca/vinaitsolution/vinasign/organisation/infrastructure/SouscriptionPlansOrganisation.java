package ca.vinaitsolution.vinasign.organisation.infrastructure;

import ca.vinaitsolution.vinasign.commun.application.port.SouscriptionPlans;
import ca.vinaitsolution.vinasign.commun.domain.IdentifiantOrganisation;
import ca.vinaitsolution.vinasign.organisation.application.AdministrerOrganisations;
import org.springframework.stereotype.Component;

/** Plans changés par la facturation (VS-121) : par le cas d'utilisation de l'organisation, audit compris. */
@Component
class SouscriptionPlansOrganisation implements SouscriptionPlans {

    private final AdministrerOrganisations organisations;

    SouscriptionPlansOrganisation(AdministrerOrganisations organisations) {
        this.organisations = organisations;
    }

    @Override
    public void appliquer(IdentifiantOrganisation organisation, String plan) {
        organisations.appliquerPlan(organisation, plan);
    }

    @Override
    public void revenirAuPlanParDefaut(IdentifiantOrganisation organisation) {
        organisations.revenirAuPlanParDefaut(organisation);
    }
}
