package ca.vinaitsolution.vinasign.signature.infrastructure.pades;

import static org.junit.jupiter.api.Assertions.*;

import ca.vinaitsolution.vinasign.signature.application.port.CleDeSignature;
import ca.vinaitsolution.vinasign.signature.cle.Certificats;
import ca.vinaitsolution.vinasign.signature.domain.AlgorithmeSignature;
import ca.vinaitsolution.vinasign.signature.infrastructure.cle.ClePkcs12;
import com.sun.net.httpserver.HttpServer;
import java.io.ByteArrayOutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.bouncycastle.asn1.pkcs.PKCSObjectIdentifiers;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cms.CMSProcessableByteArray;
import org.bouncycastle.cms.CMSSignedData;
import org.bouncycastle.cms.SignerInformation;
import org.bouncycastle.cms.jcajce.JcaSimpleSignerInfoVerifierBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Ce que PadesLtaTest ne couvre pas : CAdES détaché vérifié, clé ECDSA, refus d'horodatage, clé sans certificat. */
class CachetPadesTest {

    private static final Clock HORLOGE = Clock.fixed(Instant.parse("2026-11-22T12:00:00Z"), ZoneOffset.UTC);
    private static final byte[] CONTENU = "Note clinique — empreinte".getBytes(StandardCharsets.UTF_8);

    private static void verifier(byte[] p7s, byte[] contenu) throws Exception {
        CMSSignedData cms = new CMSSignedData(new CMSProcessableByteArray(contenu), p7s);
        SignerInformation si = cms.getSignerInfos().getSigners().iterator().next();
        X509CertificateHolder cert = (X509CertificateHolder) cms.getCertificates().getMatches(si.getSID()).iterator().next();
        assertTrue(si.verify(new JcaSimpleSignerInfoVerifierBuilder().build(cert)));
        assertNotNull(si.getSignedAttributes().get(PKCSObjectIdentifiers.id_aa_signingCertificateV2), "CAdES-B : signing-certificate-v2");
    }

    @Test
    void la_signature_detachee_rsa_ou_ecdsa_se_verifie_et_le_niveau_reflete_l_horodatage(@TempDir Path dossier) throws Exception {
        for (String algorithme : List.of("RSA", "EC")) {
            ClePkcs12 cle = new ClePkcs12(Certificats.magasin(dossier, algorithme, "s".toCharArray()), "s".toCharArray());
            CachetPades cachet = new CachetPades(cle, " ", HORLOGE);
            assertEquals("PAdES-B-B", cachet.niveauPades());
            verifier(cachet.signerDetache(CONTENU), CONTENU);
            assertEquals("CN=Cachet d'essai", cachet.titulaire());
            try (PDDocument doc = PDDocument.load(cachet.scellerPdf(pdf(), "Essai " + algorithme))) {
                assertEquals(1, doc.getSignatureDictionaries().size(), algorithme);
            }
        }
        assertEquals(AlgorithmeSignature.ECDSA_SHA256,
                new ClePkcs12(Certificats.magasin(dossier, "EC", "s".toCharArray()), "s".toCharArray()).algorithme());
    }

    @Test
    void une_autorite_d_horodatage_qui_refuse_fait_echouer_la_signature(@TempDir Path dossier) throws Exception {
        HttpServer serveur = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        serveur.createContext("/refus", ex -> {
            ex.sendResponseHeaders(503, -1);
            ex.close();
        });
        serveur.start();
        try {
            CachetPades cachet = new CachetPades(new ClePkcs12(Certificats.magasin(dossier, "RSA", "s".toCharArray()), "s".toCharArray()),
                    "http://127.0.0.1:" + serveur.getAddress().getPort() + "/refus", HORLOGE);
            assertEquals("PAdES-B-LTA", cachet.niveauPades());
            assertTrue(assertThrows(IllegalStateException.class, () -> cachet.signerDetache(CONTENU)).getMessage().contains("503"));
        } finally {
            serveur.stop(0);
        }
        CachetPades injoignable = new CachetPades(new ClePkcs12(Certificats.magasin(dossier, "RSA", "s".toCharArray()), "s".toCharArray()),
                "http://127.0.0.1:1/tsa", HORLOGE);
        assertThrows(IllegalStateException.class, () -> injoignable.signerDetache(CONTENU));
    }

    @Test
    void une_cle_sans_certificat_ou_qui_echoue_est_signalee() {
        CleDeSignature sansCertificat = new CleDeSignature() {
            public byte[] signerEmpreinte(byte[] sha256) {
                throw new IllegalStateException("HSM indisponible");
            }

            public AlgorithmeSignature algorithme() {
                return AlgorithmeSignature.RSA_SHA256;
            }

            public List<java.security.cert.X509Certificate> chaine() {
                return List.of();
            }
        };
        assertThrows(IllegalStateException.class, () -> new CachetPades(sansCertificat, null, HORLOGE));
    }

    private static byte[] pdf() throws Exception {
        try (PDDocument doc = new PDDocument(); ByteArrayOutputStream sortie = new ByteArrayOutputStream()) {
            doc.addPage(new PDPage());
            doc.save(sortie);
            return sortie.toByteArray();
        }
    }

    @Test
    void une_empreinte_se_signe_sans_le_document_et_se_verifie_avec_lui(@TempDir Path dossier) throws Exception {
        CachetPades cachet = new CachetPades(new ClePkcs12(Certificats.magasin(dossier, "RSA", "s".toCharArray()), "s".toCharArray()), null, HORLOGE);
        byte[] cms = cachet.signerEmpreinte(java.security.MessageDigest.getInstance("SHA-256").digest(CONTENU));
        verifier(cms, CONTENU);
        CMSSignedData altere = new CMSSignedData(new CMSProcessableByteArray("autre".getBytes(StandardCharsets.UTF_8)), cms);
        SignerInformation si = altere.getSignerInfos().getSigners().iterator().next();
        X509CertificateHolder cert = (X509CertificateHolder) altere.getCertificates().getMatches(si.getSID()).iterator().next();
        assertThrows(org.bouncycastle.cms.CMSSignerDigestMismatchException.class,
                () -> si.verify(new JcaSimpleSignerInfoVerifierBuilder().build(cert)), "un autre document ne correspond pas à l'empreinte");
        assertThrows(IllegalArgumentException.class, () -> cachet.signerEmpreinte(new byte[31]));
        assertThrows(IllegalArgumentException.class, () -> cachet.signerEmpreinte(null));
    }
}
