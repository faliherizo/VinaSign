package ca.vinaitsolution.vinasign.commun.domain;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class CondensatsTest {

    @Test
    void empreinte_sha256_connue_et_algorithme_absent_signale() {
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", Condensats.sha256Hex("abc"));
        assertEquals(32, Condensats.sha256(new byte[0]).length);
        assertThrows(IllegalStateException.class, () -> Condensats.condenser("SHA-INEXISTANT", new byte[0]));
    }

    @Test
    void un_echec_cryptographique_devient_une_erreur_qui_nomme_l_operation() {
        assertEquals("ok", OperationCryptographique.executer("Essai", () -> "ok"));
        IllegalStateException e = assertThrows(IllegalStateException.class, () -> OperationCryptographique.executer("Chiffrement", () -> {
            throw new java.security.NoSuchAlgorithmException("absent");
        }));
        assertEquals("Chiffrement impossible.", e.getMessage());
        assertInstanceOf(java.security.NoSuchAlgorithmException.class, e.getCause());
    }
}
