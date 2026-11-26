package ca.vinaitsolution.vinasign.enveloppe.infrastructure;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.encryption.AccessPermission;
import org.apache.pdfbox.pdmodel.encryption.StandardProtectionPolicy;

/** Documents d'essai produits à chaque exécution : jamais de fichier binaire figé dans le dépôt. */
public final class Pdfs {

    private Pdfs() {
    }

    public static byte[] pages(int n) {
        return produire(n, null);
    }

    public static byte[] chiffre() {
        return produire(1, "secret-essai");
    }

    /**
     * Pages US Letter (612 × 792 pt) portant chacune ses textes, Helvetica 12 pt, chacun à la position (x, y) donnée en points
     * depuis le coin bas-gauche : {@code texte, x, y} répétés.
     */
    public static byte[] avecTextes(Object[]... pages) {
        try (PDDocument doc = new PDDocument(); ByteArrayOutputStream sortie = new ByteArrayOutputStream()) {
            for (Object[] textes : pages) {
                PDPage page = new PDPage(org.apache.pdfbox.pdmodel.common.PDRectangle.LETTER);
                doc.addPage(page);
                try (var flux = new org.apache.pdfbox.pdmodel.PDPageContentStream(doc, page)) {
                    for (int i = 0; i < textes.length; i += 3) {
                        flux.beginText();
                        flux.setFont(org.apache.pdfbox.pdmodel.font.PDType1Font.HELVETICA, 12);
                        flux.newLineAtOffset(((Number) textes[i + 1]).floatValue(), ((Number) textes[i + 2]).floatValue());
                        flux.showText((String) textes[i]);
                        flux.endText();
                    }
                }
            }
            doc.save(sortie);
            return sortie.toByteArray();
        } catch (java.io.IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }

    /** Protégé par un mot de passe de propriétaire seulement : il s'ouvre sans mot de passe, mais reste chiffré. */
    public static byte[] protegeSansMotDePasse() {
        try (PDDocument doc = new PDDocument(); ByteArrayOutputStream sortie = new ByteArrayOutputStream()) {
            doc.addPage(new PDPage());
            StandardProtectionPolicy p = new StandardProtectionPolicy("proprietaire", "", new AccessPermission());
            p.setEncryptionKeyLength(128);
            doc.protect(p);
            doc.save(sortie);
            return sortie.toByteArray();
        } catch (java.io.IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }

    private static byte[] produire(int n, String motDePasse) {
        try (PDDocument doc = new PDDocument(); ByteArrayOutputStream sortie = new ByteArrayOutputStream()) {
            for (int i = 0; i < n; i++) doc.addPage(new PDPage());
            if (motDePasse != null) {
                StandardProtectionPolicy p = new StandardProtectionPolicy(motDePasse, motDePasse, new AccessPermission());
                p.setEncryptionKeyLength(128);
                doc.protect(p);
            }
            doc.save(sortie);
            return sortie.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
