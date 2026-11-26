package ca.vinaitsolution.vinasign.commun;

import static org.junit.jupiter.api.Assertions.*;

import ca.vinaitsolution.vinasign.commun.domain.CodeLangue;
import ca.vinaitsolution.vinasign.commun.domain.IdentifiantOrganisation;
import ca.vinaitsolution.vinasign.commun.domain.Libelles;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;

class SocleDomaineTest {

    @Test
    void une_langue_est_normalisee() {
        assertEquals("fr", new CodeLangue(" FR ").valeur());
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "f", "fra", "f1", "é"})
    void une_langue_mal_formee_est_refusee(String saisie) {
        assertThrows(IllegalArgumentException.class, () -> new CodeLangue(saisie));
    }

    @Test
    void un_libelle_retombe_sur_la_langue_de_repli_puis_sur_le_code() {
        Libelles l = Libelles.de(Map.of("fr", " Français ", "en", "French"));
        assertEquals("French", l.pour(new CodeLangue("en"), new CodeLangue("fr"), "x"));
        assertEquals("Français", l.pour(new CodeLangue("es"), new CodeLangue("fr"), "x"), "repli, espaces retirés");
        assertEquals("x", Libelles.de(Map.of()).pour(new CodeLangue("es"), new CodeLangue("fr"), "x"));
    }

    @Test
    void les_libelles_vides_sont_ignores_et_la_carte_est_immuable() {
        Map<String, String> brut = new HashMap<>();
        brut.put("fr", " ");
        brut.put("en", null);
        Libelles l = Libelles.de(brut);
        assertTrue(l.parLangue().isEmpty());
        assertTrue(Libelles.de(null).parLangue().isEmpty());
        assertThrows(UnsupportedOperationException.class, () -> l.parLangue().put(new CodeLangue("fr"), "x"));
    }

    @Test
    void un_identifiant_d_organisation_est_lu_ou_refuse() {
        java.util.UUID u = java.util.UUID.randomUUID();
        assertEquals(u, IdentifiantOrganisation.depuis(" " + u + " ").valeur());
        assertThrows(IllegalArgumentException.class, () -> IdentifiantOrganisation.depuis("x"));
        assertThrows(IllegalArgumentException.class, () -> IdentifiantOrganisation.depuis(null));
        assertThrows(IllegalArgumentException.class, () -> new IdentifiantOrganisation(null));
    }
}
