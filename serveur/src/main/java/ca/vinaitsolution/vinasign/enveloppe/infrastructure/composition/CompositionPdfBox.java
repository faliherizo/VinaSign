package ca.vinaitsolution.vinasign.enveloppe.infrastructure.composition;

import ca.vinaitsolution.vinasign.enveloppe.application.port.CompositionDocument;
import ca.vinaitsolution.vinasign.enveloppe.domain.Apposition;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.util.Matrix;
import org.springframework.stereotype.Component;

/**
 * Apposition des signatures et des valeurs sur le PDF (VS-070), avec PDFBox : chaque apposition est dessinée dans le
 * rectangle de son champ, mise à l'échelle sans déformation ; les pages ne changent pas. Le texte utilise une police
 * standard (Helvetica) : un caractère qu'elle ne sait pas coder est remplacé, plutôt que d'interrompre la finalisation.
 */
@Component
class CompositionPdfBox implements CompositionDocument {

    private static final float TAILLE_MAX = 12f;
    private static final float TAILLE_MIN = 6f;

    @Override
    public byte[] composer(byte[] pdf, List<Apposition> appositions) {
        try (PDDocument doc = PDDocument.load(pdf); ByteArrayOutputStream sortie = new ByteArrayOutputStream()) {
            for (Apposition a : appositions) {
                if (a.page() > doc.getNumberOfPages()) {
                    throw new IllegalArgumentException("Champ hors du document : page " + a.page() + ".");
                }
                apposer(doc, doc.getPage(a.page() - 1), a);
            }
            doc.save(sortie);
            return sortie.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("Composition du document final impossible.", e);
        }
    }

    /**
     * Dessine dans un repère local, posé dans le coin inférieur gauche du champ tel que la page est affichée : une page
     * pivotée ({@code /Rotate}) reçoit la signature là où le préparateur l'a placée à l'écran, et à l'endroit (VS-060).
     */
    private static void apposer(PDDocument doc, PDPage page, Apposition a) throws IOException {
        PDRectangle boite = page.getCropBox();
        boolean couchee = page.getRotation() % 180 != 0;
        float largeurVue = couchee ? boite.getHeight() : boite.getWidth();
        float hauteurVue = couchee ? boite.getWidth() : boite.getHeight();
        float l = (float) a.largeur() * largeurVue;
        float h = (float) a.hauteur() * hauteurVue;
        try (PDPageContentStream flux = new PDPageContentStream(doc, page, PDPageContentStream.AppendMode.APPEND, true, true)) {
            flux.transform(repere(page.getRotation(), boite, (float) a.x() * largeurVue, (float) a.y() * hauteurVue + h));
            if (a.image() != null) {
                PDImageXObject image = PDImageXObject.createFromByteArray(doc, a.image(), "signature");
                float echelle = Math.min(l / image.getWidth(), h / image.getHeight());
                flux.drawImage(image, 0, 0, image.getWidth() * echelle, image.getHeight() * echelle);
            } else {
                float taille = Math.max(TAILLE_MIN, Math.min(TAILLE_MAX, h * 0.7f));
                flux.beginText();
                flux.setFont(PDType1Font.HELVETICA, taille);
                flux.newLineAtOffset(1, (h - taille) / 2 + 1);
                flux.showText(codable(a.texte()));
                flux.endText();
            }
        }
    }

    /** Repère dont l'origine est à {@code gauche} et {@code bas} (depuis le haut) de la page affichée, axes à l'endroit. */
    private static Matrix repere(int rotation, PDRectangle boite, float gauche, float bas) {
        float x0 = boite.getLowerLeftX();
        float y0 = boite.getLowerLeftY();
        float x1 = boite.getUpperRightX();
        float y1 = boite.getUpperRightY();
        return switch (rotation) {
            case 90 -> new Matrix(0, 1, -1, 0, x0 + bas, y0 + gauche);
            case 180 -> new Matrix(-1, 0, 0, -1, x1 - gauche, y0 + bas);
            case 270 -> new Matrix(0, -1, 1, 0, x1 - bas, y1 - gauche);
            default -> new Matrix(1, 0, 0, 1, x0 + gauche, y1 - bas);
        };
    }

    /** Caractères que l'encodage WinAnsi d'Helvetica ne connaît pas : remplacés par « ? » ; sauts de ligne : espaces. */
    static String codable(String texte) {
        StringBuilder b = new StringBuilder();
        texte.replaceAll("\\s+", " ").codePoints().forEach(c -> {
            String car = new String(Character.toChars(c));
            try {
                PDType1Font.HELVETICA.encode(car);
                b.append(car);
            } catch (IOException | IllegalArgumentException e) {
                b.append('?');
            }
        });
        return b.toString();
    }
}
