package ca.vinaitsolution.vinasign.organisation.infrastructure;

import static org.junit.jupiter.api.Assertions.*;

import ca.vinaitsolution.vinasign.commun.domain.ContexteAcces;
import ca.vinaitsolution.vinasign.commun.domain.IdentifiantOrganisation;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ControleOrganisationActiveTest {

    private final IdentifiantOrganisation bloquee = new IdentifiantOrganisation(UUID.randomUUID());
    private final ControleOrganisationActive controle = new ControleOrganisationActive(bloquee::equals);

    @Test
    void une_organisation_bloquee_est_refusee_et_une_active_passe() {
        assertEquals("organisation-suspendue",
                controle.verifier(new ContexteAcces(Optional.of(bloquee), Optional.empty())).orElseThrow().type());
        assertTrue(controle.verifier(new ContexteAcces(Optional.of(new IdentifiantOrganisation(UUID.randomUUID())), Optional.empty()))
                .isEmpty());
        assertTrue(controle.verifier(ContexteAcces.anonyme()).isEmpty());
    }
}
