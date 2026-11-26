package ca.vinaitsolution.vinasign.enveloppe.infrastructure.controle;

import static org.junit.jupiter.api.Assertions.*;

import ca.vinaitsolution.vinasign.enveloppe.application.port.LocalisationAncres.Emplacement;
import ca.vinaitsolution.vinasign.enveloppe.infrastructure.Pdfs;
import java.io.UncheckedIOException;
import java.util.List;
import org.junit.jupiter.api.Test;

class LocalisationPdfBoxTest {

    private final LocalisationPdfBox localisation = new LocalisationPdfBox();

    @Test
    void chaque_occurrence_de_l_ancre_est_rapportee_a_sa_page_en_coordonnees_normalisees() {
        byte[] pdf = Pdfs.avecTextes(new Object[] {"Signature : {{signature:1}}", 72, 100},
                new Object[] {"{{signature:1}}", 306, 396, "{{initiales:2}}", 72, 700});
        List<Emplacement> e = localisation.localiser(pdf, "{{signature:1}}");
        assertEquals(2, e.size());
        assertEquals(1, e.get(0).page());
        assertTrue(e.get(0).x() > 72.0 / 612, "après « Signature : »");
        assertEquals(1 - 100.0 / 792, e.get(0).y() + e.get(0).hauteur(), 0.01, "ligne de base à 100 pt du bas");
        assertEquals(2, e.get(1).page());
        assertEquals(0.5, e.get(1).x(), 0.005, "au milieu de la page");
        assertTrue(e.get(1).largeur() > 0.1 && e.get(1).hauteur() > 0.005);
        assertEquals(List.of(), localisation.localiser(pdf, "{{absente}}"));
        assertEquals(1, localisation.localiser(pdf, "{{initiales:2}}").size());
    }

    @Test
    void un_document_illisible_est_signale() {
        assertThrows(UncheckedIOException.class, () -> localisation.localiser("pas un pdf".getBytes(), "{{x}}"));
    }
}
