package ca.vinaitsolution.vinasign.organisation;

import static org.junit.jupiter.api.Assertions.*;

import ca.vinaitsolution.vinasign.commun.application.port.StatutOrganisations;
import ca.vinaitsolution.vinasign.commun.domain.IdentifiantOrganisation;
import ca.vinaitsolution.vinasign.organisation.application.port.Organisations;
import ca.vinaitsolution.vinasign.organisation.domain.Organisation;
import ca.vinaitsolution.vinasign.support.PostgresDeTest;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@SpringBootTest
@Import(PostgresDeTest.class)
class OrganisationsJdbcContratTest extends ContratOrganisations {

    @Autowired Organisations organisations;
    @Autowired StatutOrganisations statuts;

    @Override
    protected Organisations organisations() {
        return organisations;
    }

    @Test
    void seule_une_organisation_active_et_connue_n_est_pas_bloquee() {
        Organisation o = Organisation.creer(new IdentifiantOrganisation(UUID.randomUUID()), "Organisation", "plan-essai", Instant.now());
        organisations.ajouter(o);
        assertFalse(statuts.estBloquee(o.id()));
        organisations.remplacer(o.suspendre(Instant.now()));
        assertTrue(statuts.estBloquee(o.id()));
        assertTrue(statuts.estBloquee(new IdentifiantOrganisation(UUID.randomUUID())), "une organisation inconnue est refusée");
    }
}
