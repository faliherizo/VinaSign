package ca.vinaitsolution.vinasign.acces;

import ca.vinaitsolution.vinasign.acces.application.port.ApplicationsClientes;
import ca.vinaitsolution.vinasign.acces.application.port.RegistreApplications;
import ca.vinaitsolution.vinasign.commun.domain.IdentifiantOrganisation;
import ca.vinaitsolution.vinasign.support.ApplicationsDeTest;
import ca.vinaitsolution.vinasign.support.OrganisationDeTest;
import ca.vinaitsolution.vinasign.support.PostgresDeTest;
import ca.vinaitsolution.vinasign.support.ReferentielsSql;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/** Sous le rôle applicatif et la RLS : l'organisation courante commande ce qui est écrit et lu. */
@SpringBootTest
@Import({PostgresDeTest.class, OrganisationDeTest.class, ReferentielsSql.class, ApplicationsDeTest.class})
class ApplicationsClientesJdbcContratTest extends ContratApplicationsClientes {

    @Autowired ApplicationsClientes applications;
    @Autowired RegistreApplications registre;
    @Autowired OrganisationDeTest organisation;
    @Autowired ApplicationsDeTest donnees;

    @Override
    protected ApplicationsClientes applications() {
        return applications;
    }

    @Override
    protected RegistreApplications registre() {
        return registre;
    }

    @Override
    protected void agirPour(IdentifiantOrganisation o) {
        if (o == null) {
            organisation.anonyme();
            return;
        }
        donnees.organisationActive(o.valeur());
        organisation.agirPour(o);
    }
}
