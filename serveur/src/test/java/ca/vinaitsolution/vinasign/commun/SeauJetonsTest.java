package ca.vinaitsolution.vinasign.commun;

import static org.junit.jupiter.api.Assertions.*;

import ca.vinaitsolution.vinasign.commun.domain.SeauJetons;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class SeauJetonsTest {

    private static final Instant T0 = Instant.parse("2026-11-22T18:00:00Z");

    @Test
    void un_seau_plein_accepte_sa_capacite_puis_refuse_avec_le_delai_du_prochain_jeton() {
        SeauJetons seau = SeauJetons.plein(3, T0);
        for (int i = 0; i < 3; i++) {
            SeauJetons.Decision d = seau.tenter(T0);
            assertTrue(d.acceptee());
            assertEquals(Duration.ZERO, d.attente());
            seau = d.seau();
        }
        SeauJetons.Decision refus = seau.tenter(T0);
        assertFalse(refus.acceptee());
        assertEquals(Duration.ofSeconds(20), refus.attente(), "3 par minute : un jeton toutes les 20 s");
    }

    @Test
    void le_seau_se_recharge_avec_le_temps_sans_depasser_sa_capacite() {
        SeauJetons vide = SeauJetons.plein(60, T0);
        for (int i = 0; i < 60; i++) vide = vide.tenter(T0).seau();
        assertFalse(vide.tenter(T0).acceptee());
        assertTrue(vide.tenter(T0.plusSeconds(1)).acceptee(), "60 par minute : un jeton par seconde");
        SeauJetons apresUneHeure = vide.tenter(T0.plusSeconds(3600)).seau();
        assertEquals(59, apresUneHeure.jetons(), 1e-9, "plafonné à la capacité, moins le jeton consommé");
    }

    @Test
    void une_horloge_qui_recule_ne_recharge_rien_et_ne_remonte_pas_la_date() {
        SeauJetons seau = SeauJetons.plein(1, T0).tenter(T0).seau();
        SeauJetons.Decision d = seau.tenter(T0.minusSeconds(30));
        assertFalse(d.acceptee());
        assertEquals(T0, d.seau().derniereRecharge());
        assertEquals(Duration.ofMinutes(1), d.attente());
    }

    @Test
    void un_seau_n_est_oubliable_qu_une_fois_plein() {
        SeauJetons seau = SeauJetons.plein(2, T0).tenter(T0).seau();
        assertFalse(seau.estPleinA(T0));
        assertFalse(seau.estPleinA(T0.minusSeconds(10)));
        assertTrue(seau.estPleinA(T0.plusSeconds(30)));
    }

    @Test
    void un_seau_sans_capacite_ou_sans_date_est_refuse() {
        assertThrows(IllegalArgumentException.class, () -> SeauJetons.plein(0, T0));
        assertThrows(IllegalArgumentException.class, () -> new SeauJetons(1, 1, null));
    }
}
