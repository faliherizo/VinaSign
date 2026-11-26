package ca.vinaitsolution.vinasign.signature.infrastructure.pades;

import java.io.IOException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.util.Comparator;
import java.util.Optional;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.interactive.digitalsignature.PDSignature;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cms.CMSException;
import org.bouncycastle.cms.CMSSignedData;
import org.bouncycastle.tsp.TSPException;
import org.bouncycastle.tsp.TimeStampToken;

/**
 * Échéance du dernier horodatage de document d'un PDF (VS-132) : fin de validité du certificat de l'autorité qui l'a
 * signé. Le dernier est celui dont la plage couverte s'étend le plus loin dans le fichier (révisions incrémentales).
 */
final class EcheanceHorodatage {

    private static final COSName RFC3161 = COSName.getPDFName("ETSI.RFC3161");

    private EcheanceHorodatage() {
    }

    static Optional<Instant> de(byte[] pdf) {
        try (PDDocument doc = PDDocument.load(pdf)) {
            Optional<PDSignature> dernier = doc.getSignatureDictionaries().stream()
                    .filter(s -> RFC3161.getName().equals(s.getSubFilter()))
                    .max(Comparator.comparingLong(s -> (long) s.getByteRange()[2] + s.getByteRange()[3]));
            if (dernier.isEmpty()) {
                return Optional.empty();
            }
            return Optional.of(certificatSignataire(new TimeStampToken(new CMSSignedData(ContenuSignature.sansBourrage(dernier.get().getContents(pdf))))).getNotAfter().toInstant());
        } catch (IOException | CMSException | TSPException | CertificateException e) {
            throw new IllegalStateException("Horodatage de document illisible : " + e.getMessage(), e);
        }
    }

    private static X509Certificate certificatSignataire(TimeStampToken jeton) throws CertificateException {
        java.util.Collection<X509CertificateHolder> trouves = jeton.getCertificates().getMatches(jeton.getSID());
        if (trouves.isEmpty()) {
            throw new CertificateException("certificat de l'autorité absent du jeton");
        }
        return new JcaX509CertificateConverter().getCertificate(trouves.iterator().next());
    }
}
