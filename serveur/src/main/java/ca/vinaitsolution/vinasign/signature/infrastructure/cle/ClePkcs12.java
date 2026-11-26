package ca.vinaitsolution.vinasign.signature.infrastructure.cle;

import ca.vinaitsolution.vinasign.signature.application.port.CleDeSignature;
import ca.vinaitsolution.vinasign.signature.domain.AlgorithmeSignature;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.Key;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.security.interfaces.ECPrivateKey;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Clé de cachet dans un magasin PKCS#12 (VS-031) : postes de développement et bacs à sable seulement. Son emploi est
 * refusé en production par la configuration (la clé y serait un fichier, pas une clé de HSM non exportable).
 */
public class ClePkcs12 implements CleDeSignature {

    private final PrivateKey cle;
    private final List<X509Certificate> chaine;
    private final AlgorithmeSignature algorithme;

    public ClePkcs12(Path magasin, char[] motDePasse) {
        try (InputStream entree = Files.newInputStream(magasin)) {
            KeyStore ks = KeyStore.getInstance("PKCS12");
            ks.load(entree, motDePasse);
            String alias = Collections.list(ks.aliases()).stream().filter(a -> estCle(ks, a)).findFirst()
                    .orElseThrow(() -> new IllegalStateException("Le magasin ne contient aucune clé privée."));
            Key k = ks.getKey(alias, motDePasse);
            this.cle = (PrivateKey) k;
            this.chaine = Arrays.stream(ks.getCertificateChain(alias)).map(X509Certificate.class::cast).toList();
            this.algorithme = k instanceof ECPrivateKey ? AlgorithmeSignature.ECDSA_SHA256 : AlgorithmeSignature.RSA_SHA256;
        } catch (IOException | GeneralSecurityException e) {
            throw new IllegalStateException("Magasin PKCS#12 du cachet illisible.", e);
        } finally {
            Arrays.fill(motDePasse, '\0');
        }
    }

    private static boolean estCle(KeyStore ks, String alias) {
        return ca.vinaitsolution.vinasign.commun.domain.OperationCryptographique.executer("Lecture du magasin", () -> ks.isKeyEntry(alias));
    }

    @Override
    public byte[] signerEmpreinte(byte[] sha256) {
        Empreintes.exigerSha256(sha256);
        return ca.vinaitsolution.vinasign.commun.domain.OperationCryptographique.executer("Signature avec la clé du magasin", () -> {
            boolean ec = algorithme == AlgorithmeSignature.ECDSA_SHA256;
            Signature s = Signature.getInstance(ec ? "NONEwithECDSA" : "NONEwithRSA");
            s.initSign(cle);
            s.update(ec ? sha256 : digestInfo(sha256));
            return s.sign();
        });
    }

    /** DigestInfo DER d'un SHA-256 (RFC 8017, § 9.2, note 1) : préfixe fixe suivi de l'empreinte, pour RSASSA-PKCS1-v1_5. */
    static byte[] digestInfo(byte[] sha256) {
        byte[] prefixe = java.util.HexFormat.of().parseHex("3031300d060960864801650304020105000420");
        byte[] r = Arrays.copyOf(prefixe, prefixe.length + sha256.length);
        System.arraycopy(sha256, 0, r, prefixe.length, sha256.length);
        return r;
    }

    @Override
    public AlgorithmeSignature algorithme() {
        return algorithme;
    }

    @Override
    public List<X509Certificate> chaine() {
        return chaine;
    }

    /** Sans la clé : rien de sensible dans une trace. */
    @Override
    public String toString() {
        return "ClePkcs12[" + chaine.get(0).getSubjectX500Principal().getName() + "]";
    }
}
