package ca.vinaitsolution.vinasign.commun;

import static org.junit.jupiter.api.Assertions.*;

import ca.vinaitsolution.vinasign.commun.infrastructure.identifiant.GenerateurUuidV7;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** RFC 9562 : version 7, variante IETF, horodatage en tête, aléa ailleurs. */
class GenerateurUuidV7Test {

    private static final long INSTANT = 1_795_000_000_123L;

    private static GenerateurUuidV7 generateur(byte remplissage) {
        SecureRandom fixe = new SecureRandom() {
            @Override
            public void nextBytes(byte[] octets) {
                java.util.Arrays.fill(octets, remplissage);
            }
        };
        return new GenerateurUuidV7(Clock.fixed(Instant.ofEpochMilli(INSTANT), ZoneOffset.UTC), fixe);
    }

    @Test
    void la_version_la_variante_et_l_horodatage_sont_poses() {
        UUID u = generateur((byte) 0xFF).suivant();
        assertEquals(7, u.version());
        assertEquals(2, u.variant());
        assertEquals(INSTANT, u.getMostSignificantBits() >>> 16, "48 bits de millisecondes en tête");
    }

    @Test
    void l_alea_remplit_le_reste_et_ne_deborde_pas_sur_l_horodatage() {
        UUID plein = generateur((byte) 0xFF).suivant();
        UUID vide = generateur((byte) 0x00).suivant();
        assertNotEquals(plein, vide, "l'aléa change l'identifiant");
        assertEquals(plein.getMostSignificantBits() >>> 16, vide.getMostSignificantBits() >>> 16);
        assertEquals(0x0FFFL, plein.getMostSignificantBits() & 0x0FFFL);
        assertEquals(0L, vide.getMostSignificantBits() & 0x0FFFL);
        assertEquals(2, vide.variant());
    }

    @Test
    void deux_identifiants_successifs_different() {
        GenerateurUuidV7 reel = new GenerateurUuidV7(Clock.systemUTC(), new SecureRandom());
        assertNotEquals(reel.suivant(), reel.suivant());
    }
}
