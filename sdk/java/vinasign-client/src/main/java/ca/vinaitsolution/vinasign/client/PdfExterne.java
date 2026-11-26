package ca.vinaitsolution.vinasign.client;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.time.Instant;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.TimeZone;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.interactive.digitalsignature.ExternalSigningSupport;
import org.apache.pdfbox.pdmodel.interactive.digitalsignature.PDSignature;
import org.apache.pdfbox.pdmodel.interactive.digitalsignature.SignatureOptions;

/**
 * Signature d'un PDF qui reste chez l'application (VS-092, mode empreinte) : le PDF reçoit un emplacement de signature
 * PAdES, l'empreinte de la plage couverte est envoyée à VinaSign (POST /api/v1/empreintes), et la signature CMS rendue
 * est intégrée. Le document lui-même n'est jamais transmis. Requiert PDFBox (dépendance facultative du SDK).
 */
public final class PdfExterne implements AutoCloseable {

    private static final int TAILLE_RESERVEE = 32_768;

    private final PDDocument document;
    private final SignatureOptions options;
    private final ExternalSigningSupport externe;
    private final ByteArrayOutputStream sortie;
    private final byte[] empreinte;

    private PdfExterne(PDDocument document, SignatureOptions options, ExternalSigningSupport externe, ByteArrayOutputStream sortie, byte[] empreinte) {
        this.document = document;
        this.options = options;
        this.externe = externe;
        this.sortie = sortie;
        this.empreinte = empreinte;
    }

    /** Prépare l'emplacement de signature ; {@link #empreinte()} est alors à faire signer. */
    public static PdfExterne preparer(byte[] pdf, String motif, Instant le) {
        try {
            PDDocument doc = PDDocument.load(pdf);
            PDSignature signature = new PDSignature();
            signature.setFilter(PDSignature.FILTER_ADOBE_PPKLITE);
            signature.setSubFilter(PDSignature.SUBFILTER_ETSI_CADES_DETACHED);
            signature.setReason(motif);
            Calendar date = new GregorianCalendar(TimeZone.getTimeZone("UTC"));
            date.setTimeInMillis(le.toEpochMilli());
            signature.setSignDate(date);
            SignatureOptions options = new SignatureOptions();
            options.setPreferredSignatureSize(TAILLE_RESERVEE);
            doc.addSignature(signature, options);
            ByteArrayOutputStream sortie = new ByteArrayOutputStream();
            ExternalSigningSupport externe = doc.saveIncrementalForExternalSigning(sortie);
            try (InputStream couvert = externe.getContent()) {
                return new PdfExterne(doc, options, externe, sortie, Empreintes.sha256(couvert.readAllBytes()));
            }
        } catch (IOException e) {
            throw new UncheckedIOException("PDF illisible ou non signable.", e);
        }
    }

    /** SHA-256 de la plage d'octets couverte par la signature. */
    public byte[] empreinte() {
        return empreinte.clone();
    }

    /** Intègre la signature CMS rendue par VinaSign ; rend le PDF signé. */
    public byte[] integrer(byte[] cms) {
        try (this) {
            externe.setSignature(cms);
            return sortie.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("Signature non intégrable (plus grande que l'espace réservé ?).", e);
        }
    }

    @Override
    public void close() throws IOException {
        options.close();
        document.close();
    }
}
