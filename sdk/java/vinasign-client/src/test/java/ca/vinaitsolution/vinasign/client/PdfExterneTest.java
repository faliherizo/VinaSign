package ca.vinaitsolution.vinasign.client;

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayOutputStream;
import java.math.BigInteger;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.interactive.digitalsignature.PDSignature;
import org.bouncycastle.asn1.cms.AttributeTable;
import org.bouncycastle.asn1.cms.CMSAttributes;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.cms.CMSSignedData;
import org.bouncycastle.cms.CMSSignedDataGenerator;
import org.bouncycastle.cms.CMSAbsentContent;
import org.bouncycastle.cms.SignerInformation;
import org.bouncycastle.cms.jcajce.JcaSignerInfoGeneratorBuilder;
import org.bouncycastle.cms.jcajce.JcaSimpleSignerInfoVerifierBuilder;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.bouncycastle.operator.jcajce.JcaDigestCalculatorProviderBuilder;
import org.bouncycastle.asn1.DEROctetString;
import org.bouncycastle.asn1.DERSet;
import org.bouncycastle.asn1.cms.Attribute;
import org.bouncycastle.cms.DefaultSignedAttributeTableGenerator;
import org.junit.jupiter.api.Test;

class PdfExterneTest {

    private static byte[] pdf() throws Exception {
        try (PDDocument doc = new PDDocument(); ByteArrayOutputStream o = new ByteArrayOutputStream()) {
            doc.addPage(new PDPage());
            doc.save(o);
            return o.toByteArray();
        }
    }

    /** CMS détaché sur une empreinte, comme le rend VinaSign (POST /api/v1/empreintes) : messageDigest = empreinte fournie. */
    private static byte[] cmsSurEmpreinte(byte[] empreinte, KeyPair cles, X509CertificateHolder certificat) throws Exception {
        var signataire = new JcaContentSignerBuilder("SHA256withRSA").build(cles.getPrivate());
        var attributs = new DefaultSignedAttributeTableGenerator(new AttributeTable(new Attribute(CMSAttributes.messageDigest,
                new DERSet(new DEROctetString(empreinte))))) {
            @Override
            protected java.util.Hashtable createStandardAttributeTable(java.util.Map parametres) {
                java.util.Hashtable t = super.createStandardAttributeTable(parametres);
                t.put(CMSAttributes.messageDigest, new Attribute(CMSAttributes.messageDigest, new DERSet(new DEROctetString(empreinte))));
                return t;
            }
        };
        CMSSignedDataGenerator g = new CMSSignedDataGenerator();
        g.addSignerInfoGenerator(new JcaSignerInfoGeneratorBuilder(new JcaDigestCalculatorProviderBuilder().build())
                .setSignedAttributeGenerator(attributs).build(signataire, certificat));
        g.addCertificates(new org.bouncycastle.cert.jcajce.JcaCertStore(List.of(certificat)));
        return g.generate(new CMSAbsentContent(), false).getEncoded();
    }

    @Test
    void le_pdf_reste_chez_l_application_seule_l_empreinte_est_signee_puis_integree() throws Exception {
        KeyPairGenerator g = KeyPairGenerator.getInstance("RSA");
        g.initialize(2048);
        KeyPair cles = g.generateKeyPair();
        X509CertificateHolder certificat = new JcaX509v3CertificateBuilder(new X500Name("CN=Cachet d'essai"), BigInteger.ONE, new Date(),
                new Date(System.currentTimeMillis() + 86_400_000L), new X500Name("CN=Cachet d'essai"), cles.getPublic())
                .build(new JcaContentSignerBuilder("SHA256withRSA").build(cles.getPrivate()));
        PdfExterne preparation = PdfExterne.preparer(pdf(), "Note clinique", Instant.parse("2026-11-22T12:00:00Z"));
        byte[] empreinte = preparation.empreinte();
        assertEquals(32, empreinte.length);
        byte[] signe = preparation.integrer(cmsSurEmpreinte(empreinte, cles, certificat));
        try (PDDocument doc = PDDocument.load(signe)) {
            PDSignature s = doc.getLastSignatureDictionary();
            assertEquals(PDSignature.SUBFILTER_ETSI_CADES_DETACHED.getName(), s.getSubFilter());
            assertEquals("Note clinique", s.getReason());
            byte[] couvert = s.getSignedContent(signe);
            assertArrayEquals(MessageDigest.getInstance("SHA-256").digest(couvert), empreinte, "l'empreinte signée est celle de la plage couverte");
            CMSSignedData cms = new CMSSignedData(new org.bouncycastle.cms.CMSProcessableByteArray(couvert), s.getContents(signe));
            SignerInformation si = cms.getSignerInfos().getSigners().iterator().next();
            assertTrue(si.verify(new JcaSimpleSignerInfoVerifierBuilder().build(certificat)), "signature PAdES valide sur le document");
        }
        assertThrows(java.io.UncheckedIOException.class, () -> PdfExterne.preparer("pas un pdf".getBytes(), "x", Instant.EPOCH));
        PdfExterne trop = PdfExterne.preparer(pdf(), "x", Instant.EPOCH);
        assertThrows(java.io.UncheckedIOException.class, () -> trop.integrer(new byte[40_000]), "plus grand que l'espace réservé");
    }
}
