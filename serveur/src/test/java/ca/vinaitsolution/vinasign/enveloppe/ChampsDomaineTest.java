package ca.vinaitsolution.vinasign.enveloppe;

import static org.junit.jupiter.api.Assertions.*;

import ca.vinaitsolution.vinasign.enveloppe.domain.Champ;
import ca.vinaitsolution.vinasign.enveloppe.domain.ChampsIncomplets;
import ca.vinaitsolution.vinasign.enveloppe.domain.Saisie;
import ca.vinaitsolution.vinasign.enveloppe.domain.SaisieInvalide;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ChampsDomaineTest {

    private static final UUID A = UUID.randomUUID();
    private static final UUID B = UUID.randomUUID();

    private static Champ champ(UUID signataire, String libelle, boolean obligatoire) {
        return new Champ(UUID.randomUUID(), signataire, "texte", libelle, 1, 0.1, 0.1, 0.2, 0.05, obligatoire);
    }

    @Test
    void un_champ_tient_dans_sa_page_et_porte_un_libelle() {
        assertEquals("Nom", new Champ(UUID.randomUUID(), A, "t", "  Nom ", 1, 0, 0, 1, 1, false).libelle());
        UUID id = UUID.randomUUID();
        assertThrows(IllegalArgumentException.class, () -> new Champ(null, A, "t", "l", 1, 0, 0, 0.1, 0.1, false));
        assertThrows(IllegalArgumentException.class, () -> new Champ(id, null, "t", "l", 1, 0, 0, 0.1, 0.1, false));
        assertThrows(IllegalArgumentException.class, () -> new Champ(id, A, null, "l", 1, 0, 0, 0.1, 0.1, false));
        assertThrows(IllegalArgumentException.class, () -> new Champ(id, A, " ", "l", 1, 0, 0, 0.1, 0.1, false));
        assertThrows(IllegalArgumentException.class, () -> new Champ(id, A, "t", null, 1, 0, 0, 0.1, 0.1, false));
        assertThrows(IllegalArgumentException.class, () -> new Champ(id, A, "t", "x".repeat(121), 1, 0, 0, 0.1, 0.1, false));
        assertThrows(IllegalArgumentException.class, () -> new Champ(id, A, "t", "l", 0, 0, 0, 0.1, 0.1, false));
        assertThrows(IllegalArgumentException.class, () -> new Champ(id, A, "t", "l", 1, -0.1, 0, 0.1, 0.1, false));
        assertThrows(IllegalArgumentException.class, () -> new Champ(id, A, "t", "l", 1, 0.95, 0, 0.1, 0.1, false));
        assertThrows(IllegalArgumentException.class, () -> new Champ(id, A, "t", "l", 1, 0, 0.95, 0.1, 0.1, false));
        assertThrows(IllegalArgumentException.class, () -> new Champ(id, A, "t", "l", 1, 0, 0, 0, 0.1, false));
    }

    @Test
    void un_champ_obligatoire_vide_bloque_et_le_message_le_nomme() {
        Champ nom = champ(A, "Nom", true);
        Champ note = champ(A, "Note", false);
        Champ autre = champ(B, "Date", true);
        ChampsIncomplets e = assertThrows(ChampsIncomplets.class, () -> Saisie.verifier(A, List.of(nom, note, autre), Map.of(note.id(), "x")));
        assertEquals(List.of("Nom"), e.libelles(), "le champ obligatoire d'un autre signataire ne le concerne pas");
        assertTrue(e.getMessage().contains("Nom"));
        assertThrows(ChampsIncomplets.class, () -> Saisie.verifier(A, List.of(nom), Map.of(nom.id(), "  ")));
        assertThrows(ChampsIncomplets.class, () -> Saisie.verifier(A, List.of(nom), Map.of(), java.util.Set.of()));
        Saisie.verifier(A, List.of(nom), Map.of(), java.util.Set.of(nom.id()));
        Saisie.verifier(A, List.of(nom, note, autre), Map.of(nom.id(), "Alice"));
        assertEquals("champ-etranger", assertThrows(SaisieInvalide.class, () -> Saisie.verifier(A, List.of(nom, autre),
                Map.of(nom.id(), "Alice", autre.id(), "2026-01-01"))).type());
    }
}
