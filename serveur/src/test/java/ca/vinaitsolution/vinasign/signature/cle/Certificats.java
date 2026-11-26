package ca.vinaitsolution.vinasign.signature.cle;

import java.io.FileOutputStream;
import java.math.BigInteger;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.PublicKey;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.security.spec.ECGenParameterSpec;
import java.util.Date;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;

/** Autorité d'essai et certificats de cachet produits à chaque exécution. */
public final class Certificats {

    private static final KeyPair AC = paire("RSA");

    private Certificats() {
    }

    public static KeyPair paire(String algorithme) {
        try {
            KeyPairGenerator g = KeyPairGenerator.getInstance(algorithme);
            if ("EC".equals(algorithme)) g.initialize(new ECGenParameterSpec("secp256r1"));
            else g.initialize(2048);
            return g.generateKeyPair();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /** Certificat de cachet pour cette clé publique, émis par l'autorité d'essai. */
    public static X509Certificate pour(PublicKey cle) {
        try {
            JcaX509v3CertificateBuilder b = new JcaX509v3CertificateBuilder(new X500Name("CN=AC d'essai"), BigInteger.valueOf(System.nanoTime()),
                    new Date(System.currentTimeMillis() - 60_000), new Date(System.currentTimeMillis() + 86_400_000L),
                    new X500Name("CN=Cachet d'essai"), cle);
            return new JcaX509CertificateConverter().getCertificate(b.build(new JcaContentSignerBuilder("SHA256withRSA").build(AC.getPrivate())));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /** Magasin PKCS#12 contenant une clé de cet algorithme et son certificat. */
    public static Path magasin(Path dossier, String algorithme, char[] motDePasse) {
        try {
            KeyPair p = paire(algorithme);
            KeyStore ks = KeyStore.getInstance("PKCS12");
            ks.load(null, null);
            ks.setCertificateEntry("autorite", pour(AC.getPublic()));
            ks.setKeyEntry("cachet", p.getPrivate(), motDePasse, new Certificate[] {pour(p.getPublic())});
            Path f = dossier.resolve(algorithme + ".p12");
            try (FileOutputStream sortie = new FileOutputStream(f.toFile())) {
                ks.store(sortie, motDePasse);
            }
            return f;
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
