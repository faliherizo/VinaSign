package ca.vinaitsolution.vinasign.session;

import static org.junit.jupiter.api.Assertions.*;

import ca.vinaitsolution.vinasign.session.domain.CleAcces;
import ca.vinaitsolution.vinasign.session.domain.CleAccesRefusee;
import ca.vinaitsolution.vinasign.session.domain.DefiCleAcces;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CleAccesDomaineTest {

    private static final Instant T = Instant.parse("2026-11-22T12:00:00Z");
    private static final UUID S = UUID.randomUUID();

    @Test
    void un_defi_ne_sert_qu_a_sa_ceremonie_et_avant_son_echeance() {
        byte[] octets = new byte[32];
        DefiCleAcces d = new DefiCleAcces(S, DefiCleAcces.AUTHENTIFICATION, octets, T.plusSeconds(300));
        octets[0] = 9;
        assertEquals(0, d.exiger(DefiCleAcces.AUTHENTIFICATION, T)[0], "copie défensive");
        d.defi()[0] = 9;
        assertEquals(0, d.defi()[0]);
        assertThrows(CleAccesRefusee.class, () -> d.exiger(DefiCleAcces.ENREGISTREMENT, T), "autre cérémonie");
        assertThrows(CleAccesRefusee.class, () -> d.exiger(DefiCleAcces.AUTHENTIFICATION, T.plusSeconds(300)), "expiré");
        assertThrows(IllegalArgumentException.class, () -> new DefiCleAcces(null, "n", new byte[32], T));
        assertThrows(IllegalArgumentException.class, () -> new DefiCleAcces(S, null, new byte[32], T));
        assertThrows(IllegalArgumentException.class, () -> new DefiCleAcces(S, "n", null, T));
        assertThrows(IllegalArgumentException.class, () -> new DefiCleAcces(S, "n", new byte[15], T), "défi trop court");
        assertThrows(IllegalArgumentException.class, () -> new DefiCleAcces(S, "n", new byte[32], null));
    }

    @Test
    void une_cle_est_complete_et_protege_ses_donnees() {
        byte[] donnees = {1, 2};
        CleAcces c = new CleAcces(S, "i", "cred", donnees, 0, T);
        donnees[0] = 9;
        assertEquals(1, c.donnees()[0]);
        c.donnees()[0] = 9;
        assertEquals(1, c.donnees()[0]);
        assertThrows(IllegalArgumentException.class, () -> new CleAcces(null, "i", "c", new byte[1], 0, T));
        assertThrows(IllegalArgumentException.class, () -> new CleAcces(S, null, "c", new byte[1], 0, T));
        assertThrows(IllegalArgumentException.class, () -> new CleAcces(S, "i", null, new byte[1], 0, T));
        assertThrows(IllegalArgumentException.class, () -> new CleAcces(S, "i", " ", new byte[1], 0, T));
        assertThrows(IllegalArgumentException.class, () -> new CleAcces(S, "i", "c", null, 0, T));
        assertThrows(IllegalArgumentException.class, () -> new CleAcces(S, "i", "c", new byte[1], -1, T));
        assertThrows(IllegalArgumentException.class, () -> new CleAcces(S, "i", "c", new byte[1], 0, null));
    }
}
