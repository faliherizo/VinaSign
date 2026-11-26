package ca.vinaitsolution.vinasign.enveloppe.infrastructure.composition;

import static org.junit.jupiter.api.Assertions.*;

import ca.vinaitsolution.vinasign.enveloppe.domain.Apposition;
import ca.vinaitsolution.vinasign.enveloppe.infrastructure.Pdfs;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.UncheckedIOException;
import java.util.List;
import javax.imageio.ImageIO;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class CompositionPdfBoxTest {

    private final CompositionPdfBox composition = new CompositionPdfBox();

    @Test
    void le_texte_est_appose_sur_la_bonne_page_sans_changer_le_nombre_de_pages() throws Exception {
        byte[] compose = composition.composer(Pdfs.pages(2), List.of(new Apposition(2, 0.1, 0.1, 0.5, 0.05, "Québec\nNord", null)));
        try (PDDocument doc = PDDocument.load(compose)) {
            assertEquals(2, doc.getNumberOfPages());
            PDFTextStripper lecteur = new PDFTextStripper();
            lecteur.setStartPage(2);
            lecteur.setEndPage(2);
            assertTrue(lecteur.getText(doc).contains("Québec Nord"));
        }
    }

    /** Rendu réel de la page : l'encre tombe dans le champ tel qu'affiché, quelle que soit la rotation, et nulle part ailleurs. */
    @ParameterizedTest
    @ValueSource(ints = {0, 90, 180, 270})
    void la_signature_tombe_ou_elle_a_ete_placee_a_l_ecran_meme_sur_une_page_pivotee(int rotation) throws Exception {
        byte[] pdf;
        try (PDDocument doc = new PDDocument(); ByteArrayOutputStream o = new ByteArrayOutputStream()) {
            PDPage page = new PDPage(new PDRectangle(20, 30, 400, 600));
            page.setRotation(rotation);
            doc.addPage(page);
            doc.save(o);
            pdf = o.toByteArray();
        }
        // Image rouge en haut, bleue en bas : elle doit aussi rester à l'endroit.
        BufferedImage image = new BufferedImage(200, 100, BufferedImage.TYPE_INT_RGB);
        for (int x = 0; x < 200; x++) {
            for (int y = 0; y < 100; y++) {
                image.setRGB(x, y, y < 50 ? 0xFF0000 : 0x0000FF);
            }
        }
        ByteArrayOutputStream png = new ByteArrayOutputStream();
        ImageIO.write(image, "png", png);
        byte[] compose = composition.composer(pdf, List.of(new Apposition(1, 0.5, 0.1, 0.4, 0.1, null, png.toByteArray())));
        try (PDDocument doc = PDDocument.load(compose)) {
            BufferedImage vue = new PDFRenderer(doc).renderImage(0);
            int gauche = vue.getWidth();
            int droite = 0;
            int haut = vue.getHeight();
            int bas = 0;
            for (int x = 0; x < vue.getWidth(); x++) {
                for (int y = 0; y < vue.getHeight(); y++) {
                    if ((vue.getRGB(x, y) & 0xFFFFFF) != 0xFFFFFF) {
                        gauche = Math.min(gauche, x);
                        droite = Math.max(droite, x);
                        haut = Math.min(haut, y);
                        bas = Math.max(bas, y);
                    }
                }
            }
            assertTrue(gauche >= 0.5 * vue.getWidth() - 1 && droite <= 0.9 * vue.getWidth() + 1, "horizontal : " + gauche + "–" + droite);
            assertTrue(haut >= 0.1 * vue.getHeight() - 1 && bas <= 0.2 * vue.getHeight() + 1, "vertical : " + haut + "–" + bas);
            int milieu = (gauche + droite) / 2;
            assertEquals(0xFF0000, vue.getRGB(milieu, haut + 3) & 0xFFFFFF, "rouge en haut");
            assertEquals(0x0000FF, vue.getRGB(milieu, bas - 3) & 0xFFFFFF, "bleu en bas");
        }
    }

    @Test
    void un_caractere_que_la_police_ne_sait_pas_coder_est_remplace() {
        assertEquals("Lévis ? ?", CompositionPdfBox.codable("Lévis 字 😀"));
    }

    @Test
    void un_champ_hors_du_document_ou_un_fichier_illisible_sont_refuses() {
        assertThrows(IllegalArgumentException.class, () -> composition.composer(Pdfs.pages(1), List.of(new Apposition(3, 0, 0, 0.1, 0.1, "x", null))));
        assertThrows(UncheckedIOException.class, () -> composition.composer("pas un pdf".getBytes(), List.of()));
    }

    @Test
    void une_apposition_porte_un_texte_ou_une_image_et_protege_ses_octets() {
        assertThrows(IllegalArgumentException.class, () -> new Apposition(1, 0, 0, 0.1, 0.1, null, null));
        assertThrows(IllegalArgumentException.class, () -> new Apposition(1, 0, 0, 0.1, 0.1, "x", new byte[] {1}));
        assertThrows(IllegalArgumentException.class, () -> new Apposition(0, 0, 0, 0.1, 0.1, "x", null));
        byte[] image = {1, 2};
        Apposition a = new Apposition(1, 0, 0, 0.1, 0.1, null, image);
        image[0] = 9;
        assertEquals(1, a.image()[0]);
        a.image()[0] = 9;
        assertEquals(1, a.image()[0]);
        assertNull(new Apposition(1, 0, 0, 0.1, 0.1, "x", null).image());
    }
}
