package ca.vinaitsolution.vinasign.acces.infrastructure.jdbc;

import static org.junit.jupiter.api.Assertions.*;

import ca.vinaitsolution.vinasign.acces.application.port.UtilisateursPortail;
import ca.vinaitsolution.vinasign.acces.domain.Courriel;
import ca.vinaitsolution.vinasign.acces.domain.OrganisationInactive;
import ca.vinaitsolution.vinasign.acces.domain.UtilisateurPortail;
import ca.vinaitsolution.vinasign.commun.domain.IdentifiantOrganisation;
import ca.vinaitsolution.vinasign.support.ApplicationsDeTest;
import ca.vinaitsolution.vinasign.support.OrganisationDeTest;
import ca.vinaitsolution.vinasign.support.PostgresDeTest;
import ca.vinaitsolution.vinasign.support.ReferentielsSql;
import java.util.Set;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.liquibase.LiquibaseDataSource;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
@Import({PostgresDeTest.class, OrganisationDeTest.class, ReferentielsSql.class, ApplicationsDeTest.class})
class UtilisateursJdbcTest {

    @Autowired UtilisateursPortail utilisateurs;
    @Autowired ApplicationsDeTest donnees;
    @Autowired @LiquibaseDataSource DataSource proprietaire;

    private static UtilisateurPortail invite(UUID organisation) {
        return UtilisateurPortail.inviter(UUID.randomUUID(), new IdentifiantOrganisation(organisation),
                new Courriel("u-" + UUID.randomUUID() + "@exemple.ca"), Set.of("administrateur-organisation"));
    }

    @Test
    void la_plateforme_n_invite_personne_dans_une_organisation_inconnue_ou_suspendue() {
        assertThrows(OrganisationInactive.class, () -> utilisateurs.ajouterPourLaPlateforme(invite(UUID.randomUUID())));
        UUID suspendue = UUID.randomUUID();
        donnees.organisationActive(suspendue);
        new JdbcTemplate(proprietaire).update("UPDATE organisation SET statut = 'suspendue' WHERE id = ?", suspendue);
        assertThrows(OrganisationInactive.class, () -> utilisateurs.ajouterPourLaPlateforme(invite(suspendue)));
        assertEquals(0, new JdbcTemplate(proprietaire).queryForObject("SELECT count(*) FROM utilisateur_portail WHERE tenant_id = ?",
                Integer.class, suspendue));
    }
}
