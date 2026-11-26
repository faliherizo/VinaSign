package ca.vinaitsolution.vinasign.acces.infrastructure.securite;

import static org.junit.jupiter.api.Assertions.*;

import java.security.KeyPairGenerator;
import java.time.Duration;
import java.util.Base64;
import org.junit.jupiter.api.Test;

/** Clés de jeton, réglages et adaptateurs sans état : décisions qui ne passent pas par le point de jeton. */
class SecuriteJetonsTest {

    private static JetonsProprietes proprietes(String pem, boolean ephemere) {
        return new JetonsProprietes("https://auth.essai", "https://api.essai", Duration.ofMinutes(5), Duration.ofMinutes(5), pem, ephemere);
    }

    @Test
    void sans_cle_fournie_ni_cle_ephemere_autorisee_le_serveur_refuse_de_demarrer() {
        IllegalStateException e = assertThrows(IllegalStateException.class, () -> ClesDeJeton.cle(proprietes(null, false)));
        assertTrue(e.getMessage().contains("VINASIGN_JETONS_CLE_PRIVEE"));
        assertThrows(IllegalStateException.class, () -> ClesDeJeton.cle(proprietes(" ", false)));
    }

    @Test
    void une_cle_pem_fournie_est_lue_et_identifiee_par_son_empreinte() throws Exception {
        KeyPairGenerator g = KeyPairGenerator.getInstance("RSA");
        g.initialize(2048);
        String pem = "-----BEGIN PRIVATE KEY-----\n" + Base64.getMimeEncoder().encodeToString(g.generateKeyPair().getPrivate().getEncoded())
                + "\n-----END PRIVATE KEY-----";
        var cle = ClesDeJeton.cle(proprietes(pem, false));
        assertTrue(cle.isPrivate());
        assertEquals(cle.computeThumbprint().toString(), cle.getKeyID());
        assertEquals(cle.getKeyID(), ClesDeJeton.cle(proprietes(pem, false)).getKeyID(), "même clé, même identifiant");
    }

    @Test
    void une_cle_pem_illisible_est_signalee() {
        assertThrows(IllegalStateException.class, () -> ClesDeJeton.cle(proprietes("-----BEGIN PRIVATE KEY-----AAAA-----END PRIVATE KEY-----", false)));
    }

    @Test
    void une_cle_ephemere_n_est_generee_que_si_elle_est_autorisee() {
        assertTrue(ClesDeJeton.cle(proprietes(null, true)).isPrivate());
    }

    @Test
    void les_reglages_ne_divulguent_pas_la_cle() {
        assertFalse(proprietes("SECRET-PEM", true).toString().contains("SECRET"));
    }

    @Test
    void le_depot_des_clients_refuse_l_enregistrement_direct_et_les_autorisations_ne_sont_pas_conservees() {
        ClientsEnregistres clients = new ClientsEnregistres(c -> java.util.Optional.empty(), o -> false, proprietes(null, true));
        assertThrows(UnsupportedOperationException.class, () -> clients.save(null));
        assertNull(clients.findById("essai-12345678"));
        AutorisationsSansEtat autorisations = new AutorisationsSansEtat();
        autorisations.save(null);
        autorisations.remove(null);
        assertNull(autorisations.findById("x"));
        assertNull(autorisations.findByToken("x", null));
    }
}
