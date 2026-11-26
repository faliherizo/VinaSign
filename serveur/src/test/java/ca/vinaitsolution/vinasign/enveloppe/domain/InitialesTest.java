package ca.vinaitsolution.vinasign.enveloppe.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class InitialesTest {

    @Test
    void la_premiere_lettre_de_chaque_partie_du_nom_accents_compris() {
        assertEquals("CG", Initiales.de("Camille Gagnon"));
        assertEquals("JPÉ", Initiales.de("  Jean-Paul élie "));
        assertEquals("ABCDE", Initiales.de("a b c d e f"));
    }

    @Test
    void un_nom_absent_ou_sans_lettre_n_a_pas_d_initiales() {
        assertEquals("", Initiales.de(null));
        assertEquals("", Initiales.de(" 42 "));
    }
}
