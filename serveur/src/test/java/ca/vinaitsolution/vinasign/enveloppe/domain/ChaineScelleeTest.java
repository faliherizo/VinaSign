package ca.vinaitsolution.vinasign.enveloppe.domain;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ChaineScelleeTest {

    private static final UUID E = UUID.fromString("00000000-0000-0000-0000-000000000042");

    private static List<ChaineScellee.Maillon> chaine(int n) {
        List<ChaineScellee.Maillon> maillons = new ArrayList<>();
        String precedente = ChaineScellee.ORIGINE;
        for (int i = 1; i <= n; i++) {
            ChaineScellee.Maillon brut = new ChaineScellee.Maillon(i, "type-" + i, i == 2 ? null : "app|x", 1_000_000L * i, i == 3 ? null : "{}", precedente, null);
            String empreinte = ChaineScellee.empreinte(precedente, E, brut);
            maillons.add(new ChaineScellee.Maillon(i, brut.type(), brut.acteur(), brut.microsecondes(), brut.donnees(), precedente, empreinte));
            precedente = empreinte;
        }
        return maillons;
    }

    @Test
    void une_chaine_intacte_est_integre() {
        assertEquals(new ChaineScellee.Verification(true, Optional.empty()), ChaineScellee.verifier(E, chaine(3)));
        assertTrue(ChaineScellee.verifier(E, List.of()).integre());
    }

    @Test
    void une_modification_une_suppression_ou_une_insertion_rompt_la_chaine() {
        List<ChaineScellee.Maillon> m = chaine(3);
        ChaineScellee.Maillon deux = m.get(1);
        List<ChaineScellee.Maillon> modifiee = List.of(m.get(0), new ChaineScellee.Maillon(2, "autre", deux.acteur(), deux.microsecondes(), deux.donnees(),
                deux.empreintePrecedente(), deux.empreinte()), m.get(2));
        assertEquals(Optional.of(2L), ChaineScellee.verifier(E, modifiee).rupture());
        assertEquals(Optional.of(2L), ChaineScellee.verifier(E, List.of(m.get(0), m.get(2))).rupture(), "suppression");
        ChaineScellee.Maillon trois = m.get(2);
        assertEquals(Optional.of(3L), ChaineScellee.verifier(E, List.of(m.get(0), m.get(1), new ChaineScellee.Maillon(3, trois.type(), trois.acteur(),
                trois.microsecondes(), trois.donnees(), "f".repeat(64), trois.empreinte()))).rupture(), "maillon rattaché ailleurs");
        assertFalse(ChaineScellee.verifier(UUID.randomUUID(), m).integre(), "chaîne d'une autre enveloppe");
    }

    @Test
    void le_separateur_dans_un_champ_ne_permet_pas_de_deplacer_une_frontiere() {
        ChaineScellee.Maillon a = new ChaineScellee.Maillon(1, "a|b", "c", 1, "{}", ChaineScellee.ORIGINE, null);
        ChaineScellee.Maillon b = new ChaineScellee.Maillon(1, "a", "b|c", 1, "{}", ChaineScellee.ORIGINE, null);
        assertNotEquals(ChaineScellee.empreinte(ChaineScellee.ORIGINE, E, a), ChaineScellee.empreinte(ChaineScellee.ORIGINE, E, b));
    }

    @Test
    void agregat_et_algorithme_indisponible() {
        assertEquals(ChaineScellee.sha256("ab".getBytes()), ChaineScellee.agreger(List.of("a", "b")));
        assertThrows(IllegalStateException.class, () -> ChaineScellee.condenser("SHA-INEXISTANT", new byte[0]));
    }

    @Test
    void un_ancrage_est_complet_et_protege_sa_signature() {
        Instant t = Instant.parse("2026-11-22T10:00:00Z");
        byte[] cms = {1, 2};
        Ancrage a = new Ancrage(UUID.randomUUID(), t, t.plusSeconds(3600), 4, "e".repeat(64), cms, t.plusSeconds(3601));
        cms[0] = 9;
        assertEquals(1, a.signature()[0]);
        a.signature()[0] = 9;
        assertEquals(1, a.signature()[0]);
        UUID id = UUID.randomUUID();
        assertThrows(IllegalArgumentException.class, () -> new Ancrage(null, t, t.plusSeconds(1), 0, "e", cms, t));
        assertThrows(IllegalArgumentException.class, () -> new Ancrage(id, null, t, 0, "e", cms, t));
        assertThrows(IllegalArgumentException.class, () -> new Ancrage(id, t, null, 0, "e", cms, t));
        assertThrows(IllegalArgumentException.class, () -> new Ancrage(id, t, t, 0, "e", cms, t), "fenêtre vide");
        assertThrows(IllegalArgumentException.class, () -> new Ancrage(id, t, t.plusSeconds(1), -1, "e", cms, t));
        assertThrows(IllegalArgumentException.class, () -> new Ancrage(id, t, t.plusSeconds(1), 0, null, cms, t));
        assertThrows(IllegalArgumentException.class, () -> new Ancrage(id, t, t.plusSeconds(1), 0, "e", null, t));
        assertThrows(IllegalArgumentException.class, () -> new Ancrage(id, t, t.plusSeconds(1), 0, "e", cms, null));
    }
}
