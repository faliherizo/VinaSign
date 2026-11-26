package ca.vinaitsolution.vinasign.session.infrastructure.identite;

import static org.junit.jupiter.api.Assertions.*;

import ca.vinaitsolution.vinasign.session.application.port.FournisseurIdentite;
import ca.vinaitsolution.vinasign.session.domain.VerificationIdentiteIndisponible;
import ca.vinaitsolution.vinasign.session.domain.VerificationIdentiteRefusee;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;

/** VS-131 — le rappel n'est cru que signé du secret partagé ; un fournisseur absent n'offre rien et n'accepte rien. */
class FournisseurIdentiteTest {

    private static final String SECRET = "secret-partage-de-test-de-32-caracteres-au-moins";
    private static final String URL = "https://idv.exemple.test/v?r={reference}&l={langue}";
    private final FournisseurIdentiteGenerique generique = new FournisseurIdentiteGenerique("generique", URL, SECRET, new ObjectMapper());

    private static String signer(String corps) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return "sha256=" + HexFormat.of().formatHex(mac.doFinal(corps.getBytes(StandardCharsets.UTF_8)));
    }

    private FournisseurIdentite.Conclusion conclure(String corps) throws Exception {
        return generique.conclure(corps.getBytes(StandardCharsets.UTF_8), signer(corps));
    }

    @Test
    void l_adresse_porte_la_reference_et_la_langue_encodees() {
        assertEquals("generique", generique.code());
        assertEquals("https://idv.exemple.test/v?r=a%2Bb&l=fr", generique.demarrer(new FournisseurIdentite.Demande("a+b", "fr")));
    }

    @Test
    void un_rappel_signe_et_complet_est_lu() throws Exception {
        assertEquals(new FournisseurIdentite.Conclusion("r1", true, "piece", "eleve"),
                conclure("{\"reference\":\"r1\",\"resultat\":\"reussie\",\"methode\":\"piece\",\"assurance\":\"eleve\"}"));
        FournisseurIdentite.Conclusion c = conclure("{\"reference\":\"r2\",\"resultat\":\"echouee\",\"methode\":\"" + "m".repeat(80) + "\"}");
        assertFalse(c.reussie());
        assertEquals(60, c.methode().length(), "méthode bornée");
        assertNull(c.assurance());
    }

    @Test
    void un_rappel_non_signe_incomplet_ou_illisible_est_refuse() throws Exception {
        byte[] corps = "{\"reference\":\"r\",\"resultat\":\"reussie\"}".getBytes(StandardCharsets.UTF_8);
        for (String signature : new String[] {null, "md5=00", "sha256=zz", "sha256=" + "0".repeat(64)}) {
            assertThrows(VerificationIdentiteRefusee.class, () -> generique.conclure(corps, signature), String.valueOf(signature));
        }
        assertThrows(VerificationIdentiteRefusee.class, () -> conclure("{\"resultat\":\"reussie\"}"));
        assertThrows(VerificationIdentiteRefusee.class, () -> conclure("{\"reference\":\"r\",\"resultat\":\"peut-etre\"}"));
        assertThrows(VerificationIdentiteRefusee.class, () -> conclure("pas du json {"));
    }

    @Test
    void une_configuration_douteuse_est_refusee_au_demarrage() {
        ObjectMapper j = new ObjectMapper();
        assertThrows(IllegalStateException.class, () -> new FournisseurIdentiteGenerique("g", null, SECRET, j));
        assertThrows(IllegalStateException.class, () -> new FournisseurIdentiteGenerique("g", "http://idv/{reference}", SECRET, j));
        assertThrows(IllegalStateException.class, () -> new FournisseurIdentiteGenerique("g", "https://idv/sans-reference", SECRET, j));
        assertThrows(IllegalStateException.class, () -> new FournisseurIdentiteGenerique("g", URL, null, j));
        assertThrows(IllegalStateException.class, () -> new FournisseurIdentiteGenerique("g", URL, "court", j));
    }

    @Test
    void sans_fournisseur_rien_n_est_offert_ni_accepte() {
        FournisseurIdentiteAucun aucun = new FournisseurIdentiteAucun();
        assertEquals("aucun", aucun.code());
        assertThrows(VerificationIdentiteIndisponible.class, () -> aucun.demarrer(new FournisseurIdentite.Demande("r", "fr")));
        assertThrows(VerificationIdentiteRefusee.class, () -> aucun.conclure(new byte[0], "x"));
    }
}
