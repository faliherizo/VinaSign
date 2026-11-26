package ca.vinaitsolution.vinasign.enveloppe.domain;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class EtatArchiveTest {

    private static final Instant T = Instant.parse("2026-11-22T12:00:00Z");

    private static EtatArchive avec(Instant echeance) {
        return new EtatArchive(UUID.randomUUID(), new IdentifiantEnveloppe(UUID.randomUUID()), "c", "0".repeat(64), 1, echeance, T);
    }

    @Test
    void seul_un_horodatage_qui_expire_avant_la_limite_est_a_renouveler() {
        assertTrue(avec(T.minusSeconds(1)).echoitAvant(T));
        assertFalse(avec(T).echoitAvant(T), "à la limite exacte : pas encore");
        assertFalse(avec(T.plusSeconds(1)).echoitAvant(T));
        assertFalse(avec(null).echoitAvant(T), "sans horodatage : rien à renouveler");
    }

    @Test
    void seuls_les_octets_archives_sont_rendus() {
        byte[] contenu = "pdf".getBytes();
        EtatArchive e = new EtatArchive(UUID.randomUUID(), new IdentifiantEnveloppe(UUID.randomUUID()), "c",
                ca.vinaitsolution.vinasign.commun.domain.Condensats.sha256Hex(contenu), 3, null, T);
        assertSame(contenu, e.verifier(contenu));
        assertThrows(EmpreinteAlteree.class, () -> e.verifier("autre".getBytes()));
    }
}
