package ca.vinaitsolution.vinasign.signature.infrastructure.pades;

import static org.junit.jupiter.api.Assertions.*;

import com.sun.net.httpserver.HttpServer;
import java.math.BigInteger;
import java.net.InetSocketAddress;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.cert.X509Certificate;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Date;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.ExtendedKeyUsage;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.KeyPurposeId;
import org.bouncycastle.cert.jcajce.JcaCertStore;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.cms.jcajce.JcaSignerInfoGeneratorBuilder;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.bouncycastle.operator.jcajce.JcaDigestCalculatorProviderBuilder;
import org.bouncycastle.tsp.TSPAlgorithms;
import org.bouncycastle.tsp.TimeStampRequest;
import org.bouncycastle.tsp.TimeStampResponseGenerator;
import org.bouncycastle.tsp.TimeStampTokenGenerator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** VS-033 — bascule entre autorités d'horodatage, et refus d'un jeton qui ne répond pas à la requête. */
class AutoritesHorodatageTest {

    private static final Clock HORLOGE = Clock.fixed(Instant.parse("2026-11-22T12:00:00Z"), ZoneOffset.UTC);
    private HttpServer serveur;
    private String base;

    @BeforeEach
    void demarrer() throws Exception {
        KeyPairGenerator g = KeyPairGenerator.getInstance("RSA");
        g.initialize(2048);
        KeyPair cle = g.generateKeyPair();
        JcaX509v3CertificateBuilder b = new JcaX509v3CertificateBuilder(new X500Name("CN=TSA"), BigInteger.ONE, new Date(0),
                new Date(4102444800000L), new X500Name("CN=TSA"), cle.getPublic());
        b.addExtension(Extension.extendedKeyUsage, true, new ExtendedKeyUsage(KeyPurposeId.id_kp_timeStamping));
        X509Certificate tsa = new JcaX509CertificateConverter().getCertificate(b.build(new JcaContentSignerBuilder("SHA256withRSA").build(cle.getPrivate())));
        serveur = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        base = "http://127.0.0.1:" + serveur.getAddress().getPort();
        serveur.createContext("/panne", ex -> {
            ex.sendResponseHeaders(503, -1);
            ex.close();
        });
        serveur.createContext("/tsa", ex -> repondre(ex, ex.getRequestBody().readAllBytes(), cle, tsa, false));
        serveur.createContext("/autre", ex -> repondre(ex, ex.getRequestBody().readAllBytes(), cle, tsa, true));
        serveur.start();
    }

    @AfterEach
    void arreter() {
        serveur.stop(0);
    }

    private static void repondre(com.sun.net.httpserver.HttpExchange ex, byte[] corps, KeyPair cle, X509Certificate tsa, boolean autreDocument)
            throws java.io.IOException {
        try {
            TimeStampRequest requete = new TimeStampRequest(corps);
            if (autreDocument) {
                requete = new org.bouncycastle.tsp.TimeStampRequestGenerator().generate(TSPAlgorithms.SHA256,
                        MessageDigest.getInstance("SHA-256").digest("autre document".getBytes()), requete.getNonce());
            }
            TimeStampTokenGenerator gen = new TimeStampTokenGenerator(new JcaSignerInfoGeneratorBuilder(new JcaDigestCalculatorProviderBuilder().build())
                    .build(new JcaContentSignerBuilder("SHA256withRSA").build(cle.getPrivate()), tsa),
                    new JcaDigestCalculatorProviderBuilder().build().get(new org.bouncycastle.asn1.x509.AlgorithmIdentifier(
                            org.bouncycastle.asn1.oiw.OIWObjectIdentifiers.idSHA1)), new org.bouncycastle.asn1.ASN1ObjectIdentifier("1.2.3.4"));
            gen.addCertificates(new JcaCertStore(java.util.List.of(tsa)));
            byte[] reponse = new TimeStampResponseGenerator(gen, TSPAlgorithms.ALLOWED).generate(requete, BigInteger.TEN, new Date()).getEncoded();
            ex.sendResponseHeaders(200, reponse.length);
            ex.getResponseBody().write(reponse);
            ex.close();
        } catch (Exception e) {
            throw new java.io.IOException(e);
        }
    }

    private static byte[] empreinte() throws Exception {
        return MessageDigest.getInstance("SHA-256").digest("document".getBytes());
    }

    @Test
    void une_autorite_en_panne_bascule_vers_la_suivante() throws Exception {
        AutoritesHorodatage a = AutoritesHorodatage.depuis(base + "/panne , " + base + "/tsa", HORLOGE);
        assertTrue(a.existe());
        assertArrayEquals(empreinte(), a.jeton(empreinte()).getTimeStampInfo().getMessageImprintDigest());
    }

    @Test
    void un_jeton_d_un_autre_document_est_refuse_puis_la_suivante_repond() throws Exception {
        assertThrows(IllegalStateException.class, () -> AutoritesHorodatage.depuis(base + "/autre", HORLOGE).jeton(empreinte()));
        assertNotNull(AutoritesHorodatage.depuis(base + "/autre," + base + "/tsa", HORLOGE).jeton(empreinte()));
    }

    @Test
    void toutes_en_echec_ou_aucune_autorite_l_horodatage_echoue() {
        assertThrows(IllegalStateException.class, () -> AutoritesHorodatage.depuis(base + "/panne," + base + "/panne", HORLOGE).jeton(new byte[32]));
        AutoritesHorodatage aucune = AutoritesHorodatage.depuis(" , ", HORLOGE);
        assertFalse(aucune.existe());
        assertFalse(AutoritesHorodatage.depuis(null, HORLOGE).existe());
        assertThrows(IllegalStateException.class, () -> aucune.jeton(new byte[32]));
    }
}
