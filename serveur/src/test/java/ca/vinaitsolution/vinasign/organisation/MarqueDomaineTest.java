package ca.vinaitsolution.vinasign.organisation;

import static org.junit.jupiter.api.Assertions.*;

import ca.vinaitsolution.vinasign.organisation.domain.Marque;
import org.junit.jupiter.api.Test;

/** VS-123 — la marque refuse ce que le signataire ne pourrait pas lire ou ce qui n'est pas une image PNG. */
class MarqueDomaineTest {

    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 13, 10};

    @Test
    void le_contraste_se_calcule_selon_wcag() {
        assertEquals(21.0, Marque.contraste("#000000", "#ffffff"), 0.01);
        assertEquals(1.0, Marque.contraste("#777777", "#777777"), 0.001);
        assertEquals(4.48, Marque.contraste("#777777", "#ffffff"), 0.01, "gris à la limite, refusé");
        assertEquals(Marque.contraste("#ffffff", "#0b5394"), Marque.contraste("#0b5394", "#ffffff"), 1e-9, "symétrique");
    }

    @Test
    void une_marque_lisible_est_normalisee_et_protege_son_logo() {
        Marque m = new Marque("  Clinique Nord ", "#0B5394", PNG, "clinique-nord");
        assertEquals("Clinique Nord", m.nomAffiche());
        assertEquals("#0b5394", m.couleurPrimaire());
        assertArrayEquals(PNG, m.logo());
        m.logo()[0] = 0;
        assertArrayEquals(PNG, m.logo(), "copie défensive");
        Marque sans = new Marque("Nord", "#000000", null, null);
        assertNull(sans.logo());
        assertNull(sans.sousDomaine());
    }

    @Test
    void ce_qui_est_illisible_ou_mal_forme_est_refuse() {
        refusee(null, "#000000", null, null, "nom");
        refusee("   ", "#000000", null, null, "nom");
        refusee("x".repeat(81), "#000000", null, null, "nom");
        refusee("Nord", null, null, null, "#rrggbb");
        refusee("Nord", "bleu", null, null, "#rrggbb");
        refusee("Nord", "#777777", null, null, "4.48:1");
        refusee("Nord", "#000000", new byte[] {1, 2}, null, "PNG");
        refusee("Nord", "#000000", new byte[] {'G', 'I', 'F', '8', '9'}, null, "PNG");
        refusee("Nord", "#000000", new byte[100_001], null, "PNG");
        refusee("Nord", "#000000", null, "Majuscule", "Sous-domaine");
        refusee("Nord", "#000000", null, "-tiret", "Sous-domaine");
    }

    private static void refusee(String nom, String couleur, byte[] logo, String sd, String motif) {
        Marque.MarqueInvalide e = assertThrows(Marque.MarqueInvalide.class, () -> new Marque(nom, couleur, logo, sd));
        assertTrue(e.getMessage().contains(motif) || e.getMessage().toLowerCase().contains(motif.toLowerCase()), e.getMessage());
        assertEquals("marque-invalide", e.type());
    }
}
