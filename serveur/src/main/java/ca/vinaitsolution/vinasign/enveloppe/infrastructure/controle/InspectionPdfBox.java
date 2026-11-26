package ca.vinaitsolution.vinasign.enveloppe.infrastructure.controle;

import ca.vinaitsolution.vinasign.enveloppe.application.port.InspectionPdf;
import ca.vinaitsolution.vinasign.enveloppe.domain.DocumentRefuse;
import java.io.IOException;
import org.apache.pdfbox.io.MemoryUsageSetting;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;

/**
 * Structure d'un PDF lue par PDFBox, mémoire bornée : un PDF chiffré ne peut pas être signé ni scellé ; un fichier
 * illisible ou sans page est refusé.
 */
public class InspectionPdfBox implements InspectionPdf {

    private final long memoireMax;

    public InspectionPdfBox(long memoireMax) {
        this.memoireMax = memoireMax;
    }

    @Override
    public int pages(byte[] contenu) {
        try (PDDocument doc = PDDocument.load(contenu, "", null, null, MemoryUsageSetting.setupMainMemoryOnly(memoireMax))) {
            if (doc.isEncrypted()) {
                throw new DocumentRefuse("document-chiffre", "Le PDF est chiffré ou protégé : le déposer sans protection.");
            }
            if (doc.getNumberOfPages() < 1) {
                throw new DocumentRefuse("document-structure", "Le PDF ne contient aucune page.");
            }
            return doc.getNumberOfPages();
        } catch (InvalidPasswordException e) {
            throw new DocumentRefuse("document-chiffre", "Le PDF est protégé par un mot de passe : le déposer sans protection.");
        } catch (IOException e) {
            throw new DocumentRefuse("document-structure", "Le PDF est illisible ou endommagé.");
        }
    }
}
