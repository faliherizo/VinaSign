package ca.vinaitsolution.vinasign.session;

import static org.junit.jupiter.api.Assertions.*;

import ca.vinaitsolution.vinasign.session.domain.CodeRefuse;
import ca.vinaitsolution.vinasign.session.domain.DefiCode;
import ca.vinaitsolution.vinasign.session.infrastructure.securite.EmpreinteHmac;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CodeDomaineTest {

    private static final Instant T0 = Instant.parse("2026-11-23T00:00:00Z");
    private static final String BON = "b".repeat(64);

    private static DefiCode defi() {
        return DefiCode.emettre(UUID.randomUUID(), UUID.randomUUID(), "courriel", BON, T0.plusSeconds(600), 5);
    }

    @Test
    void le_bon_code_valide_le_defi_une_seule_fois() {
        DefiCode valide = defi().tenter(BON, T0);
        assertNotNull(valide.valideLe());
        assertEquals("code-deja-utilise", assertThrows(CodeRefuse.class, () -> valide.tenter(BON, T0)).type());
    }

    @Test
    void cinq_essais_faux_verrouillent_et_meme_le_bon_code_est_alors_refuse() {
        DefiCode d = defi();
        for (int i = 0; i < 5; i++) {
            assertFalse(d.estVerrouille());
            d = d.tenter("f".repeat(64), T0);
        }
        assertTrue(d.estVerrouille());
        assertEquals(0, d.essaisRestants());
        DefiCode verrouille = d;
        assertEquals("code-verrouille", assertThrows(CodeRefuse.class, () -> verrouille.tenter(BON, T0)).type());
    }

    @Test
    void un_code_expire_est_refuse() {
        assertEquals("code-expire", assertThrows(CodeRefuse.class, () -> defi().tenter(BON, T0.plusSeconds(600))).type());
    }

    @Test
    void un_defi_incomplet_est_refuse() {
        UUID id = UUID.randomUUID();
        assertThrows(IllegalArgumentException.class, () -> new DefiCode(null, id, "c", BON, T0, 0, 5, null));
        assertThrows(IllegalArgumentException.class, () -> new DefiCode(id, null, "c", BON, T0, 0, 5, null));
        assertThrows(IllegalArgumentException.class, () -> new DefiCode(id, id, null, BON, T0, 0, 5, null));
        assertThrows(IllegalArgumentException.class, () -> new DefiCode(id, id, " ", BON, T0, 0, 5, null));
        assertThrows(IllegalArgumentException.class, () -> new DefiCode(id, id, "c", null, T0, 0, 5, null));
        assertThrows(IllegalArgumentException.class, () -> new DefiCode(id, id, "c", BON, null, 0, 5, null));
        assertThrows(IllegalArgumentException.class, () -> new DefiCode(id, id, "c", BON, T0, 0, 0, null));
        assertThrows(IllegalArgumentException.class, () -> new DefiCode(id, id, "c", BON, T0, -1, 5, null));
    }

    @Test
    void l_empreinte_hmac_depend_de_la_cle_du_defi_et_du_code() {
        String cle = Base64.getEncoder().encodeToString(new byte[32]);
        EmpreinteHmac h = new EmpreinteHmac(cle);
        UUID d = UUID.randomUUID();
        assertEquals(h.empreinte(d, "123456"), h.empreinte(d, "123456"));
        assertNotEquals(h.empreinte(d, "123456"), h.empreinte(d, "123457"));
        assertNotEquals(h.empreinte(d, "123456"), h.empreinte(UUID.randomUUID(), "123456"), "le même code d'un autre défi ne concorde pas");
        assertNotEquals(h.empreinte(d, "123456"), new EmpreinteHmac(Base64.getEncoder().encodeToString(new byte[] {1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11,
                12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 25, 26, 27, 28, 29, 30, 31, 32})).empreinte(d, "123456"));
        assertThrows(IllegalStateException.class, () -> new EmpreinteHmac(null));
        assertThrows(IllegalStateException.class, () -> new EmpreinteHmac(Base64.getEncoder().encodeToString(new byte[16])));
    }
}
