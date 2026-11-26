package ca.vinaitsolution.vinasign.enveloppe.infrastructure.controle;

import ca.vinaitsolution.vinasign.enveloppe.application.port.LocalisationAncres;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;
import org.springframework.stereotype.Component;

/**
 * Ancres trouvées dans le texte du PDF avec PDFBox (VS-063) : chaque caractère extrait garde sa position ; une occurrence de
 * l'ancre donne le rectangle de ses caractères, rapporté à la page. Une ancre ne franchit pas un blanc.
 */
@Component
class LocalisationPdfBox implements LocalisationAncres {

    @Override
    public List<Emplacement> localiser(byte[] pdf, String ancre) {
        try (PDDocument doc = PDDocument.load(pdf)) {
            List<Emplacement> trouves = new ArrayList<>();
            for (int p = 1; p <= doc.getNumberOfPages(); p++) {
                Collecteur c = new Collecteur();
                c.setStartPage(p);
                c.setEndPage(p);
                c.getText(doc);
                PDRectangle boite = affichee(doc.getPage(p - 1));
                for (int i = c.texte.indexOf(ancre); i >= 0; i = c.texte.indexOf(ancre, i + 1)) {
                    trouves.add(emplacement(p, boite, c.positions.subList(i, i + ancre.length())));
                }
            }
            return trouves;
        } catch (IOException e) {
            throw new UncheckedIOException("Document illisible pour la recherche d'ancres.", e);
        }
    }

    private static Emplacement emplacement(int page, PDRectangle boite, List<TextPosition> caracteres) {
        double gauche = caracteres.stream().mapToDouble(TextPosition::getXDirAdj).min().orElseThrow();
        double droite = caracteres.stream().mapToDouble(t -> t.getXDirAdj() + t.getWidthDirAdj()).max().orElseThrow();
        double haut = caracteres.stream().mapToDouble(t -> t.getYDirAdj() - t.getHeightDir()).min().orElseThrow();
        double bas = caracteres.stream().mapToDouble(TextPosition::getYDirAdj).max().orElseThrow();
        double l = boite.getWidth();
        double h = boite.getHeight();
        return new Emplacement(page, borne(gauche / l), borne(haut / h), (droite - gauche) / l, (bas - haut) / h);
    }

    /** Page telle qu'elle est affichée : une page pivotée échange sa largeur et sa hauteur. */
    private static PDRectangle affichee(org.apache.pdfbox.pdmodel.PDPage page) {
        PDRectangle b = page.getCropBox();
        return page.getRotation() % 180 == 0 ? b : new PDRectangle(b.getHeight(), b.getWidth());
    }

    private static double borne(double v) {
        return Math.max(0, Math.min(1, v));
    }

    /** Texte de la page, un caractère par position ; un blanc sépare les mots, sans position. */
    private static final class Collecteur extends PDFTextStripper {

        private final StringBuilder texte = new StringBuilder();
        private final List<TextPosition> positions = new ArrayList<>();

        private Collecteur() throws IOException {
            super();
        }

        @Override
        protected void writeString(String chaine, List<TextPosition> caracteres) {
            for (TextPosition t : caracteres) {
                for (int k = 0; k < t.getUnicode().length(); k++) {
                    texte.append(t.getUnicode().charAt(k));
                    positions.add(t);
                }
            }
            texte.append(' ');
            positions.add(null);
        }
    }
}
