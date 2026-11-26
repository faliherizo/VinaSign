package ca.vinaitsolution.vinasign.acces;

import ca.vinaitsolution.vinasign.acces.application.port.ApplicationsClientes;
import ca.vinaitsolution.vinasign.acces.application.port.RegistreApplications;
import ca.vinaitsolution.vinasign.commun.domain.IdentifiantOrganisation;
import ca.vinaitsolution.vinasign.support.ApplicationsClientesEnMemoire;
import java.util.Optional;

class ApplicationsClientesEnMemoireContratTest extends ContratApplicationsClientes {

    private IdentifiantOrganisation courante;
    private final ApplicationsClientesEnMemoire applications = new ApplicationsClientesEnMemoire(() -> Optional.ofNullable(courante));

    @Override
    protected ApplicationsClientes applications() {
        return applications;
    }

    @Override
    protected RegistreApplications registre() {
        return applications;
    }

    @Override
    protected void agirPour(IdentifiantOrganisation organisation) {
        courante = organisation;
    }
}
