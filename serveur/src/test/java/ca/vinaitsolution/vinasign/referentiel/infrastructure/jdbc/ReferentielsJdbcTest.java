package ca.vinaitsolution.vinasign.referentiel.infrastructure.jdbc;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

/** Un JSON corrompu en base est signalé, jamais masqué en liste vide. */
class ReferentielsJdbcTest {

    private final ReferentielsJdbc adaptateur = new ReferentielsJdbc(null, new ObjectMapper());

    @Test
    void un_json_illisible_est_une_corruption_signalee() {
        IllegalStateException e = assertThrows(IllegalStateException.class, () -> adaptateur.carte("{pas du json"));
        assertTrue(e.getMessage().contains("illisible"));
    }

    @Test
    void une_colonne_nulle_donne_une_carte_vide() {
        assertTrue(adaptateur.carte(null).isEmpty());
        assertEquals("1", adaptateur.carte("{\"rang\":\"1\"}").get("rang"));
    }
}
