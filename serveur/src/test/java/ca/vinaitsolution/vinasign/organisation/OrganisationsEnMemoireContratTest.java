package ca.vinaitsolution.vinasign.organisation;

import ca.vinaitsolution.vinasign.organisation.application.port.Organisations;
import ca.vinaitsolution.vinasign.support.OrganisationsEnMemoire;

class OrganisationsEnMemoireContratTest extends ContratOrganisations {

    private final OrganisationsEnMemoire organisations = new OrganisationsEnMemoire();

    @Override
    protected Organisations organisations() {
        return organisations;
    }
}
