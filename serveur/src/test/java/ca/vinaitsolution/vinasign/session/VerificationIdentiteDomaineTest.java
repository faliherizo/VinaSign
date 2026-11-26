package ca.vinaitsolution.vinasign.session;

import static org.junit.jupiter.api.Assertions.*;

import ca.vinaitsolution.vinasign.session.domain.VerificationIdentite;
import ca.vinaitsolution.vinasign.session.domain.VerificationIdentite.Etat;
import ca.vinaitsolution.vinasign.session.domain.VerificationIdentiteRefusee;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** VS-131 — une vérification se conclut une fois ; le même résultat rejoué est sans effet, un résultat contraire est refusé. */
class VerificationIdentiteDomaineTest {

    private static final Instant T = Instant.parse("2026-11-22T12:00:00Z");

    private static VerificationIdentite enCours() {
        return VerificationIdentite.demarrer(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "generique", "ref", T);
    }

    @Test
    void une_verification_se_conclut_une_seule_fois() {
        VerificationIdentite v = enCours();
        assertEquals(Etat.EN_COURS, v.etat());
        assertFalse(v.estReussie());
        VerificationIdentite r = v.conclure(true, "piece", "substantiel", T.plusSeconds(60));
        assertTrue(r.estReussie());
        assertEquals("piece", r.methode());
        assertEquals(T.plusSeconds(60), r.fin());
        assertSame(r, r.conclure(true, "autre", "autre", T.plusSeconds(120)), "rejoué : sans effet");
        assertThrows(VerificationIdentiteRefusee.class, () -> r.conclure(false, null, null, T));
        VerificationIdentite e = enCours().conclure(false, null, null, T);
        assertEquals(Etat.ECHOUEE, e.etat());
        assertThrows(VerificationIdentiteRefusee.class, () -> e.conclure(true, null, null, T));
    }

    @Test
    void les_etats_se_lisent_par_leur_code_et_une_verification_incomplete_est_refusee() {
        for (Etat e : Etat.values()) {
            assertEquals(e, Etat.deCode(e.code()));
        }
        assertThrows(IllegalArgumentException.class, () -> Etat.deCode("inconnu"));
        UUID u = UUID.randomUUID();
        assertThrows(IllegalArgumentException.class, () -> new VerificationIdentite(null, u, u, "f", "r", Etat.EN_COURS, null, null, T, null));
        assertThrows(IllegalArgumentException.class, () -> new VerificationIdentite(u, null, u, "f", "r", Etat.EN_COURS, null, null, T, null));
        assertThrows(IllegalArgumentException.class, () -> new VerificationIdentite(u, u, null, "f", "r", Etat.EN_COURS, null, null, T, null));
        assertThrows(IllegalArgumentException.class, () -> new VerificationIdentite(u, u, u, null, "r", Etat.EN_COURS, null, null, T, null));
        assertThrows(IllegalArgumentException.class, () -> new VerificationIdentite(u, u, u, "f", null, Etat.EN_COURS, null, null, T, null));
        assertThrows(IllegalArgumentException.class, () -> new VerificationIdentite(u, u, u, "f", "r", null, null, null, T, null));
        assertThrows(IllegalArgumentException.class, () -> new VerificationIdentite(u, u, u, "f", "r", Etat.EN_COURS, null, null, null, null));
    }
}
