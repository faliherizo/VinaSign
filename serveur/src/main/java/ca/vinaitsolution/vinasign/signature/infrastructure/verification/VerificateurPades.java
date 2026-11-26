package ca.vinaitsolution.vinasign.signature.infrastructure.verification;

import ca.vinaitsolution.vinasign.signature.application.dto.RapportVerification;
import ca.vinaitsolution.vinasign.signature.application.dto.RapportVerification.SignatureVerifiee;
import ca.vinaitsolution.vinasign.signature.application.port.Verificateur;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.interactive.digitalsignature.PDSignature;
import org.bouncycastle.asn1.cms.Attribute;
import org.bouncycastle.asn1.pkcs.PKCSObjectIdentifiers;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cms.CMSException;
import org.bouncycastle.cms.CMSProcessableByteArray;
import org.bouncycastle.cms.CMSSignedData;
import org.bouncycastle.cms.SignerInformation;
import org.bouncycastle.cms.jcajce.JcaSimpleSignerInfoVerifierBuilder;
import org.bouncycastle.tsp.TimeStampToken;

/**
 * Vérification d'un PDF signé ou d'une empreinte (VS-036) : chaque signature CMS est vérifiée sur les octets qu'elle
 * couvre (plage d'octets), avec son certificat ; les jetons d'horodatage (signature ou document) sont lus et vérifiés.
 * Rien n'est conservé. La confiance dans l'émetteur du certificat relève d'une validation externe (pyHanko, LTV).
 */
public class VerificateurPades implements Verificateur {

    @Override
    public RapportVerification pdf(byte[] pdf) {
        try (PDDocument doc = PDDocument.load(pdf)) {
            List<SignatureVerifiee> signatures = new ArrayList<>();
            long finSignee = 0;
            boolean dss = doc.getDocumentCatalog().getCOSObject().containsKey(COSName.getPDFName("DSS"));
            for (PDSignature s : doc.getSignatureDictionaries()) {
                int[] plage = s.getByteRange();
                if (plage != null && plage.length == 4) {
                    finSignee = Math.max(finSignee, (long) plage[2] + plage[3]);
                }
                signatures.add(verifier(s, pdf));
            }
            boolean integre = !signatures.isEmpty() && signatures.stream().allMatch(SignatureVerifiee::valide)
                    && revisionsUlterieuresSansEffetVisible(doc, pdf, finSignee);
            return new RapportVerification(integre, niveauPades(signatures, dss), signatures);
        } catch (IOException e) {
            throw new IllegalArgumentException("PDF illisible.", e);
        }
    }

    /**
     * PAdES ajoute légitimement, après la signature, des révisions sans effet visible (données de validation, horodatage
     * de document). Toute révision qui change les pages, leur contenu ou leurs annotations est une modification.
     */
    static boolean revisionsUlterieuresSansEffetVisible(PDDocument complet, byte[] pdf, long finSignee) throws IOException {
        if (finSignee >= pdf.length) {
            return true;
        }
        if (finSignee <= 0) {
            return false;
        }
        try (PDDocument signe = PDDocument.load(java.util.Arrays.copyOf(pdf, (int) finSignee))) {
            if (signe.getNumberOfPages() != complet.getNumberOfPages()) {
                return false;
            }
            for (int i = 0; i < signe.getNumberOfPages(); i++) {
                var avant = signe.getPage(i);
                var apres = complet.getPage(i);
                if (!java.util.Arrays.equals(contenu(avant), contenu(apres)) || avant.getAnnotations().size() != apres.getAnnotations().size()) {
                    return false;
                }
            }
            return true;
        }
    }

    private static byte[] contenu(org.apache.pdfbox.pdmodel.PDPage page) throws IOException {
        try (var flux = page.getContents()) {
            return flux == null ? new byte[0] : flux.readAllBytes();
        }
    }

    @Override
    public RapportVerification empreinte(byte[] sha256, byte[] cms) {
        try {
            CMSSignedData signe = new CMSSignedData(cms);
            SignerInformation si = signe.getSignerInfos().getSigners().iterator().next();
            byte[] porte = org.bouncycastle.asn1.ASN1OctetString.getInstance(si.getSignedAttributes()
                    .get(org.bouncycastle.asn1.cms.CMSAttributes.messageDigest).getAttrValues().getObjectAt(0)).getOctets();
            boolean valide = java.security.MessageDigest.isEqual(porte, sha256) && signatureSurEmpreinteValide(cms, si.getSID(), sha256);
            Instant horodatage = jetonSignature(si);
            SignatureVerifiee v = new SignatureVerifiee(sujet(signe, si), valide, horodatage, false);
            return new RapportVerification(valide, horodatage == null ? "CAdES-B-B" : "CAdES-B-T", List.of(v));
        } catch (CMSException | RuntimeException e) {
            return new RapportVerification(false, "", List.of());
        }
    }

    private static SignatureVerifiee verifier(PDSignature s, byte[] pdf) throws IOException {
        byte[] couvert = s.getSignedContent(pdf);
        byte[] contenu = ca.vinaitsolution.vinasign.signature.infrastructure.pades.ContenuSignature.sansBourrage(s.getContents(pdf));
        boolean document = "ETSI.RFC3161".equals(s.getSubFilter());
        try {
            if (document) {
                TimeStampToken jeton = new TimeStampToken(new CMSSignedData(contenu));
                boolean valide = java.security.MessageDigest.isEqual(jeton.getTimeStampInfo().getMessageImprintDigest(),
                        java.security.MessageDigest.getInstance("SHA-256").digest(couvert)) && jetonValide(jeton);
                return new SignatureVerifiee("Horodatage de document", valide, jeton.getTimeStampInfo().getGenTime().toInstant(), true);
            }
            CMSSignedData signe = new CMSSignedData(new CMSProcessableByteArray(couvert), contenu);
            SignerInformation si = signe.getSignerInfos().getSigners().iterator().next();
            return new SignatureVerifiee(sujet(signe, si), signatureValide(signe, si), jetonSignature(si), false);
        } catch (Exception e) {
            return new SignatureVerifiee("", false, null, document);
        }
    }

    /**
     * Vérifie, sans le document, la signature d'un CMS dont seule l'empreinte du contenu est connue : le calculateur
     * d'empreinte rend l'empreinte fournie ; la signature des attributs signés est vérifiée normalement.
     */
    private static boolean signatureSurEmpreinteValide(byte[] cms, org.bouncycastle.cms.SignerId sid, byte[] sha256) {
        try {
            CMSSignedData signe = new CMSSignedData(new org.bouncycastle.cms.CMSAbsentContent(), cms);
            SignerInformation si = signe.getSignerInfos().get(sid);
            X509CertificateHolder cert = (X509CertificateHolder) signe.getCertificates().getMatches(sid).iterator().next();
            org.bouncycastle.operator.DigestCalculatorProvider connue = new ca.vinaitsolution.vinasign.signature.infrastructure.pades.EmpreinteFournie(sha256);
            return si.verify(new org.bouncycastle.cms.SignerInformationVerifier(new org.bouncycastle.cms.DefaultCMSSignatureAlgorithmNameGenerator(),
                    new org.bouncycastle.operator.DefaultSignatureAlgorithmIdentifierFinder(),
                    new org.bouncycastle.operator.jcajce.JcaContentVerifierProviderBuilder().build(cert), connue));
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean signatureValide(CMSSignedData signe, SignerInformation si) {
        try {
            X509CertificateHolder cert = (X509CertificateHolder) signe.getCertificates().getMatches(si.getSID()).iterator().next();
            return si.verify(new JcaSimpleSignerInfoVerifierBuilder().build(cert));
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean jetonValide(TimeStampToken jeton) {
        return essayer(() -> {
            X509CertificateHolder cert = (X509CertificateHolder) jeton.getCertificates().getMatches(jeton.getSID()).iterator().next();
            jeton.validate(new JcaSimpleSignerInfoVerifierBuilder().build(cert));
            return true;
        }, false);
    }

    /** Vérification qui échoue (structure illisible, certificat absent, signature fausse) : la valeur de repli, jamais une exception. */
    @FunctionalInterface
    interface Verification<T> {
        T executer() throws Exception;
    }

    static <T> T essayer(Verification<T> v, T repli) {
        try {
            return v.executer();
        } catch (Exception e) {
            return repli;
        }
    }

    private static String sujet(CMSSignedData signe, SignerInformation si) {
        java.util.Collection<?> certificats = signe.getCertificates().getMatches(si.getSID());
        return certificats.stream().findFirst().map(c -> ((X509CertificateHolder) c).getSubject().toString()).orElse("");
    }

    /** Heure du jeton d'horodatage de la signature (attribut non signé), s'il est présent et valide. */
    private static Instant jetonSignature(SignerInformation si) {
        Attribute a = si.getUnsignedAttributes() == null ? null : si.getUnsignedAttributes().get(PKCSObjectIdentifiers.id_aa_signatureTimeStampToken);
        if (a == null) {
            return null;
        }
        return essayer(() -> {
            TimeStampToken jeton = new TimeStampToken(new CMSSignedData(a.getAttrValues().getObjectAt(0).toASN1Primitive().getEncoded()));
            return jetonValide(jeton) ? jeton.getTimeStampInfo().getGenTime().toInstant() : null;
        }, null);
    }

    private static String niveauPades(List<SignatureVerifiee> signatures, boolean dss) {
        if (signatures.isEmpty()) {
            return "";
        }
        if (dss && signatures.stream().anyMatch(SignatureVerifiee::horodatageDeDocument)) {
            return "PAdES-B-LTA";
        }
        return signatures.stream().anyMatch(s -> !s.horodatageDeDocument() && s.horodatage() != null) ? "PAdES-B-T" : "PAdES-B-B";
    }
}
