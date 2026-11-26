package ca.vinaitsolution.vinasign.acces.infrastructure.securite;

import static org.junit.jupiter.api.Assertions.*;

import ca.vinaitsolution.vinasign.acces.domain.ApplicationCliente;
import ca.vinaitsolution.vinasign.acces.domain.ClesPubliques;
import ca.vinaitsolution.vinasign.acces.domain.IdentifiantClient;
import ca.vinaitsolution.vinasign.commun.domain.ContexteAcces;
import ca.vinaitsolution.vinasign.commun.domain.IdentifiantOrganisation;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ControlesAccesTest {

    private static final ApplicationCliente ACTIVE = ApplicationCliente.enregistrer(UUID.randomUUID(),
            new IdentifiantOrganisation(UUID.randomUUID()), new IdentifiantClient("vs-active-1"), "App", "production",
            new ClesPubliques("{}", Set.of("e")), Set.of(), Set.of());

    private final ControleApplicationActive controle = new ControleApplicationActive(
            c -> c.equals(ACTIVE.client()) ? Optional.of(ACTIVE) : Optional.empty());

    private static ContexteAcces client(String id) {
        return new ContexteAcces(Optional.empty(), Optional.of(id));
    }

    @Test
    void une_application_active_passe() {
        assertTrue(controle.verifier(client("vs-active-1")).isEmpty());
    }

    @Test
    void une_application_revoquee_inconnue_ou_mal_nommee_est_refusee() {
        assertEquals("application-revoquee", controle.verifier(client("vs-revoquee-1")).orElseThrow().type());
        assertTrue(controle.verifier(client("x")).isPresent(), "un identifiant mal formé n'est jamais accepté");
    }

    @Test
    void une_requete_sans_application_n_est_pas_concernee() {
        assertTrue(new ControleApplicationActive(c -> fail("aucune lecture")).verifier(ContexteAcces.anonyme()).isEmpty());
    }
}
