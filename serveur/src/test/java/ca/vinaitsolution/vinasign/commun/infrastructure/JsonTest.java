package ca.vinaitsolution.vinasign.commun.infrastructure;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.junit.jupiter.api.Test;

class JsonTest {

    private final ObjectMapper json = new ObjectMapper();

    @Test
    void une_erreur_jackson_dit_ce_qui_n_a_pu_etre_lu_ou_ecrit() {
        assertEquals("{\"a\":\"1\"}", Json.ecrire(json, Map.of("a", "1"), "Essai"));
        assertEquals(Map.of("a", "1"), Json.lire(json, "{\"a\":\"1\"}", new TypeReference<Map<String, String>>() { }, "Essai"));
        assertEquals("Données non sérialisable.", assertThrows(IllegalStateException.class, () -> Json.ecrire(json, new Object(), "Données")).getMessage());
        assertEquals("Données illisible.", assertThrows(IllegalStateException.class,
                () -> Json.lire(json, "{pas du json", new TypeReference<Map<String, String>>() { }, "Données")).getMessage());
    }
}
