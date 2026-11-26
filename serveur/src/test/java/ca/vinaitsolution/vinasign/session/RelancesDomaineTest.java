package ca.vinaitsolution.vinasign.session;

import static org.junit.jupiter.api.Assertions.*;

import ca.vinaitsolution.vinasign.session.domain.LienSignature;
import ca.vinaitsolution.vinasign.session.domain.Relances;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RelancesDomaineTest {

    private static final Instant T0 = Instant.parse("2026-11-23T02:00:00Z");
    private static final Duration DEUX_JOURS = Duration.ofDays(2);

    @Test
    void une_relance_est_due_une_fois_la_frequence_ecoulee_dans_la_limite_permise() {
        assertFalse(Relances.relanceDue(T0, 0, T0.plus(Duration.ofDays(1)), DEUX_JOURS, 3));
        assertTrue(Relances.relanceDue(T0, 0, T0.plus(DEUX_JOURS), DEUX_JOURS, 3));
        assertFalse(Relances.relanceDue(T0, 3, T0.plus(DEUX_JOURS), DEUX_JOURS, 3), "maximum atteint");
        assertFalse(Relances.relanceDue(T0, 10, T0.plus(DEUX_JOURS), DEUX_JOURS, 50), "plafond de 10, quel que soit le maximum");
        assertTrue(Relances.relanceDue(T0, 9, T0.plus(DEUX_JOURS), DEUX_JOURS, 50));
        assertFalse(Relances.relanceDue(null, 0, T0, DEUX_JOURS, 3));
        assertFalse(Relances.relanceDue(T0, 0, T0.plus(DEUX_JOURS), Duration.ZERO, 3), "fréquence nulle : aucune relance");
        assertFalse(Relances.relanceDue(T0, 0, T0.plus(DEUX_JOURS), Duration.ofDays(-1), 3));
    }

    @Test
    void l_echeance_est_proche_dans_le_preavis_et_avant_son_terme() {
        Instant echeance = T0.plus(Duration.ofDays(5));
        assertTrue(Relances.echeanceProche(echeance, echeance.minus(Duration.ofHours(12)), Duration.ofDays(1)));
        assertTrue(Relances.echeanceProche(echeance, echeance, Duration.ofDays(1)));
        assertFalse(Relances.echeanceProche(echeance, echeance.plusSeconds(1), Duration.ofDays(1)), "dépassée");
        assertFalse(Relances.echeanceProche(echeance, T0, Duration.ofDays(1)), "trop tôt");
        assertFalse(Relances.echeanceProche(echeance, echeance, Duration.ZERO), "préavis nul");
        assertFalse(Relances.echeanceProche(echeance, echeance, Duration.ofDays(-1)));
        assertFalse(Relances.echeanceProche(null, T0, Duration.ofDays(1)));
    }

    @Test
    void un_lien_de_relance_porte_son_rang_a_travers_ses_transitions() {
        LienSignature l = LienSignature.emettre(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "a".repeat(64), T0.plusSeconds(60), 5).relance(2);
        assertEquals(2, l.ouvrir(T0).revoquer(T0).rang());
        assertEquals(2, l.consommer(T0).rang());
        assertThrows(IllegalArgumentException.class, () -> l.relance(-1));
    }
}
