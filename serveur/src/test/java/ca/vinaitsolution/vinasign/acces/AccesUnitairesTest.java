package ca.vinaitsolution.vinasign.acces;

import static org.junit.jupiter.api.Assertions.*;

import ca.vinaitsolution.vinasign.acces.domain.ApplicationCliente;
import ca.vinaitsolution.vinasign.acces.domain.IdentifiantClient;
import ca.vinaitsolution.vinasign.acces.domain.StatutApplication;
import ca.vinaitsolution.vinasign.commun.domain.IdentifiantOrganisation;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class AccesUnitairesTest {

    private static ApplicationCliente application(StatutApplication statut) {
        return new ApplicationCliente(UUID.randomUUID(), new IdentifiantOrganisation(UUID.randomUUID()), new IdentifiantClient("essai-12345678"),
                "Système RH", "bac-a-sable", "{\"keys\":[]}", null, null, statut, null);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"court", "avec espace", "accentué-é-12345", "x;DROP"})
    void un_identifiant_de_client_mal_forme_est_refuse(String id) {
        assertThrows(IllegalArgumentException.class, () -> new IdentifiantClient(id));
    }

    @Test
    void une_application_se_revoque_une_seule_fois() {
        ApplicationCliente a = application(StatutApplication.ACTIVE);
        assertTrue(a.active());
        assertTrue(a.portees().isEmpty());
        Instant le = Instant.parse("2026-11-22T12:00:00Z");
        ApplicationCliente r = a.revoquer(le);
        assertFalse(r.active());
        assertEquals(le, r.revoqueLe());
        assertThrows(ca.vinaitsolution.vinasign.acces.domain.TransitionApplicationInterdite.class, () -> r.revoquer(le));
    }

    @Test
    void une_application_incomplete_est_refusee() {
        IdentifiantOrganisation o = new IdentifiantOrganisation(UUID.randomUUID());
        IdentifiantClient c = new IdentifiantClient("essai-12345678");
        UUID id = UUID.randomUUID();
        assertThrows(IllegalArgumentException.class, () -> new ApplicationCliente(null, o, c, "n", "e", "{}", Set.of(), Set.of(), StatutApplication.ACTIVE, null));
        assertThrows(IllegalArgumentException.class, () -> new ApplicationCliente(id, null, c, "n", "e", "{}", Set.of(), Set.of(), StatutApplication.ACTIVE, null));
        assertThrows(IllegalArgumentException.class, () -> new ApplicationCliente(id, o, null, "n", "e", "{}", Set.of(), Set.of(), StatutApplication.ACTIVE, null));
        assertThrows(IllegalArgumentException.class, () -> new ApplicationCliente(id, o, c, "n", "e", "{}", Set.of(), Set.of(), null, null));
        assertThrows(IllegalArgumentException.class, () -> new ApplicationCliente(id, o, c, " ", "e", "{}", Set.of(), Set.of(), StatutApplication.ACTIVE, null));
        assertThrows(IllegalArgumentException.class, () -> new ApplicationCliente(id, o, c, null, "e", "{}", Set.of(), Set.of(), StatutApplication.ACTIVE, null));
        assertThrows(IllegalArgumentException.class, () -> new ApplicationCliente(id, o, c, "n", "e", " ", Set.of(), Set.of(), StatutApplication.ACTIVE, null));
        assertThrows(IllegalArgumentException.class, () -> new ApplicationCliente(id, o, c, "n", "e", null, Set.of(), Set.of(), StatutApplication.ACTIVE, null));
    }
}
