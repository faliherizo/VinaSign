package ca.vinaitsolution.vinasign.organisation;

import static org.junit.jupiter.api.Assertions.*;

import ca.vinaitsolution.vinasign.commun.domain.IdentifiantOrganisation;
import ca.vinaitsolution.vinasign.organisation.application.port.Organisations;
import ca.vinaitsolution.vinasign.organisation.domain.Organisation;
import ca.vinaitsolution.vinasign.organisation.domain.OrganisationIntrouvable;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Contrat du port {@link Organisations}, exécuté contre chaque implémentation (LSP), erreurs comprises. */
public abstract class ContratOrganisations {

    protected abstract Organisations organisations();

    private static final Instant T0 = Instant.now().truncatedTo(ChronoUnit.MILLIS);

    private static Organisation nouvelle() {
        return Organisation.creer(new IdentifiantOrganisation(UUID.randomUUID()), "Clinique Lévis", "plan-essai", T0);
    }

    @Test
    void une_organisation_ajoutee_est_relue_a_l_identique() {
        Organisation o = nouvelle();
        organisations().ajouter(o);
        assertEquals(o, organisations().parId(o.id()).orElseThrow());
    }

    @Test
    void un_remplacement_change_l_etat_relu() {
        Organisation o = nouvelle();
        organisations().ajouter(o);
        Organisation suspendue = o.suspendre(T0.plusSeconds(5));
        organisations().remplacer(suspendue);
        assertEquals(suspendue, organisations().parId(o.id()).orElseThrow());
    }

    @Test
    void remplacer_une_organisation_absente_est_refuse() {
        assertThrows(OrganisationIntrouvable.class, () -> organisations().remplacer(nouvelle()));
    }

    @Test
    void une_organisation_absente_rend_vide() {
        assertTrue(organisations().parId(new IdentifiantOrganisation(UUID.randomUUID())).isEmpty());
    }
}
