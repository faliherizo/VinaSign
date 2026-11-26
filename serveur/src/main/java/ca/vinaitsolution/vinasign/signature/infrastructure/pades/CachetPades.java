package ca.vinaitsolution.vinasign.signature.infrastructure.pades;

import ca.vinaitsolution.vinasign.signature.application.port.Cachet;
import ca.vinaitsolution.vinasign.signature.application.port.CleDeSignature;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.cert.X509Certificate;
import java.time.Clock;
import java.util.Calendar;
import java.util.Hashtable;
import java.util.List;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.io.IOUtils;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.interactive.digitalsignature.PDSignature;
import org.apache.pdfbox.pdmodel.interactive.digitalsignature.SignatureInterface;
import org.apache.pdfbox.pdmodel.interactive.digitalsignature.SignatureOptions;
import org.bouncycastle.asn1.ASN1EncodableVector;
import org.bouncycastle.asn1.ASN1ObjectIdentifier;
import org.bouncycastle.asn1.DERSet;
import org.bouncycastle.asn1.cms.Attribute;
import org.bouncycastle.asn1.cms.AttributeTable;
import org.bouncycastle.asn1.cms.CMSAttributes;
import org.bouncycastle.asn1.ess.ESSCertIDv2;
import org.bouncycastle.asn1.ess.SigningCertificateV2;
import org.bouncycastle.asn1.nist.NISTObjectIdentifiers;
import org.bouncycastle.asn1.pkcs.PKCSObjectIdentifiers;
import org.bouncycastle.asn1.x509.AlgorithmIdentifier;
import org.bouncycastle.cert.jcajce.JcaCertStore;
import org.bouncycastle.cms.CMSAbsentContent;
import org.bouncycastle.cms.CMSAttributeTableGenerator;
import org.bouncycastle.cms.CMSException;
import org.bouncycastle.cms.CMSProcessableByteArray;
import org.bouncycastle.cms.CMSSignedData;
import org.bouncycastle.cms.CMSSignedDataGenerator;
import org.bouncycastle.cms.DefaultSignedAttributeTableGenerator;
import org.bouncycastle.cms.SignerInfoGenerator;
import org.bouncycastle.cms.SignerInformation;
import org.bouncycastle.cms.SignerInformationStore;
import org.bouncycastle.cms.jcajce.JcaSignerInfoGeneratorBuilder;
import org.bouncycastle.operator.OperatorCreationException;
import org.bouncycastle.operator.jcajce.JcaDigestCalculatorProviderBuilder;
import org.bouncycastle.tsp.TimeStampToken;

/**
 * Cachet PAdES et CAdES (VS-032), porté de {@code CachetX509} du CLM sous tests de caractérisation (VS-ADR-002). Seule
 * différence de comportement : la signature passe par le port {@link CleDeSignature} (HSM en exploitation) au lieu d'une
 * clé privée chargée en mémoire. Signature CMS (CAdES-B : attribut ESS signing-certificate-v2), PDF signé en
 * PAdES (sous-filtre ETSI.CAdES.detached), DSS, horodatage de document : PAdES-B-LTA avec une autorité d'horodatage.
 */
public class CachetPades implements Cachet {

    private final CleDeSignature cle;
    private final X509Certificate certificat;
    private final List<X509Certificate> chaine;
    private final AutoritesHorodatage autorites;
    private final DonneesValidation validation = new DonneesValidation();
    private final Clock horloge;

    /** @param urlHorodatage autorités RFC 3161 dans l'ordre de préférence, séparées par des virgules ; vide : PAdES-B-B seulement */
    public CachetPades(CleDeSignature cle, String urlHorodatage, Clock horloge) {
        if (cle.chaine().isEmpty()) {
            throw new IllegalStateException("Clé de cachet sans certificat.");
        }
        this.cle = cle;
        this.chaine = cle.chaine();
        this.certificat = chaine.get(0);
        this.autorites = AutoritesHorodatage.depuis(urlHorodatage, horloge);
        this.horloge = horloge;
    }

    @Override
    public byte[] signerDetache(byte[] contenu) {
        return signer(contenu, false);
    }

    @Override
    public byte[] signerEmpreinte(byte[] sha256) {
        if (sha256 == null || sha256.length != 32) {
            throw new IllegalArgumentException("Une empreinte SHA-256 (32 octets) est attendue.");
        }
        return signer(new CMSAbsentContent(), new EmpreinteFournie(sha256), false);
    }

    @Override
    public byte[] scellerPdf(byte[] pdf, String motif) {
        byte[] lt = validation.completer(signerPdf(pdf, motif), chaine);
        return autorites.existe() ? horodaterDocument(lt) : lt;
    }

    /** Données de validation de toutes les signatures et de tous les horodatages présents, puis nouvel horodatage (VS-132). */
    @Override
    public byte[] renouvelerHorodatage(byte[] pdf) {
        if (!autorites.existe()) {
            throw new IllegalStateException("Aucune autorité d'horodatage configurée : renouvellement impossible.");
        }
        return horodaterDocument(validation.completer(pdf, chaine));
    }

    @Override
    public java.util.Optional<java.time.Instant> echeanceHorodatage(byte[] pdf) {
        return EcheanceHorodatage.de(pdf);
    }

    @Override
    public String niveauPades() {
        return autorites.existe() ? "PAdES-B-LTA" : "PAdES-B-B";
    }

    private byte[] signerPdf(byte[] pdf, String motif) {
        PDSignature signature = new PDSignature();
        signature.setFilter(PDSignature.FILTER_ADOBE_PPKLITE);
        signature.setSubFilter(PDSignature.SUBFILTER_ETSI_CADES_DETACHED);
        signature.setName(titulaire());
        signature.setReason(motif);
        Calendar quand = Calendar.getInstance();
        quand.setTimeInMillis(horloge.millis());
        signature.setSignDate(quand);
        return incrementer(pdf, signature, contenu -> signer(IOUtils.toByteArray(contenu), true));
    }

    /** Horodatage de document (ETSI.RFC3161, /DocTimeStamp) sur tout le fichier, DSS compris : niveau B-LTA. */
    private byte[] horodaterDocument(byte[] pdf) {
        PDSignature horodatage = new PDSignature();
        horodatage.getCOSObject().setItem(COSName.TYPE, COSName.getPDFName("DocTimeStamp"));
        horodatage.setFilter(PDSignature.FILTER_ADOBE_PPKLITE);
        horodatage.setSubFilter(COSName.getPDFName("ETSI.RFC3161"));
        return incrementer(pdf, horodatage, contenu -> autorites.jeton(sha256(IOUtils.toByteArray(contenu))).getEncoded());
    }

    /** Signature ajoutée en révision incrémentale : les révisions précédentes et leurs signatures restent intactes. */
    private static byte[] incrementer(byte[] pdf, PDSignature signature, SignatureInterface signataire) {
        return OperationCms.executer("Scellement PAdES", () -> {
            try (PDDocument doc = PDDocument.load(pdf); ByteArrayOutputStream sortie = new ByteArrayOutputStream()) {
                SignatureOptions o = new SignatureOptions();
                o.setPreferredSignatureSize(32_768);
                doc.addSignature(signature, signataire, o);
                doc.saveIncremental(sortie);
                return sortie.toByteArray();
            }
        });
    }

    static byte[] sha256(byte[] contenu) {
        return ca.vinaitsolution.vinasign.commun.domain.Condensats.sha256(contenu);
    }

    @Override
    public boolean reconnait(X509Certificate c) {
        if (certificat.equals(c)) {
            return true;
        }
        if (chaine.size() < 2 || !c.getSubjectX500Principal().equals(certificat.getSubjectX500Principal())) {
            return false;
        }
        X509Certificate emetteur = chaine.get(1);
        try {
            c.verify(emetteur.getPublicKey());
            return c.getIssuerX500Principal().equals(emetteur.getSubjectX500Principal());
        } catch (GeneralSecurityException e) {
            return false;
        }
    }

    @Override
    public String titulaire() {
        return certificat.getSubjectX500Principal().getName();
    }

    /** CMS détaché ; en PAdES, l'attribut signing-time est retiré : l'heure figure dans le dictionnaire de signature. */
    private byte[] signer(byte[] contenu, boolean pades) {
        return signer(new CMSProcessableByteArray(contenu), OperationCms.executer("Signature CMS", () -> new JcaDigestCalculatorProviderBuilder().build()),
                pades);
    }

    private byte[] signer(org.bouncycastle.cms.CMSTypedData contenu, org.bouncycastle.operator.DigestCalculatorProvider empreintes, boolean pades) {
        return OperationCms.executer("Signature CMS", () -> {
            SignerInfoGenerator info = new JcaSignerInfoGeneratorBuilder(empreintes)
                    .setSignedAttributeGenerator(attributs(pades)).build(new SignataireCle(cle), certificat);
            CMSSignedDataGenerator generateur = new CMSSignedDataGenerator();
            generateur.addSignerInfoGenerator(info);
            generateur.addCertificates(new JcaCertStore(chaine));
            CMSSignedData signe = generateur.generate(contenu, false);
            return (autorites.existe() ? horodater(signe) : signe).getEncoded();
        });
    }

    private CMSAttributeTableGenerator attributs(boolean pades) throws GeneralSecurityException {
        SigningCertificateV2 essV2 = new SigningCertificateV2(new ESSCertIDv2(new AlgorithmIdentifier(NISTObjectIdentifiers.id_sha256),
                sha256(certificat.getEncoded())));
        ASN1EncodableVector v = new ASN1EncodableVector();
        v.add(new Attribute(PKCSObjectIdentifiers.id_aa_signingCertificateV2, new DERSet(essV2)));
        DefaultSignedAttributeTableGenerator base = new DefaultSignedAttributeTableGenerator(new AttributeTable(v));
        return parametres -> {
            AttributeTable table = base.getAttributes(parametres);
            return pades ? table.remove(CMSAttributes.signingTime) : table;
        };
    }

    /** Jeton RFC 3161 sur la valeur de signature, ajouté comme attribut non signé (signature-time-stamp). */
    private CMSSignedData horodater(CMSSignedData signe) {
        SignerInformation si = signe.getSignerInfos().getSigners().iterator().next();
        TimeStampToken jeton = autorites.jeton(sha256(si.getSignature()));
        Hashtable<ASN1ObjectIdentifier, Attribute> nonSignes = new Hashtable<>();
        nonSignes.put(PKCSObjectIdentifiers.id_aa_signatureTimeStampToken, new Attribute(
                PKCSObjectIdentifiers.id_aa_signatureTimeStampToken, new DERSet(jeton.toCMSSignedData().toASN1Structure())));
        SignerInformation horodate = SignerInformation.replaceUnsignedAttributes(si, new AttributeTable(nonSignes));
        return CMSSignedData.replaceSigners(signe, new SignerInformationStore(horodate));
    }
}
