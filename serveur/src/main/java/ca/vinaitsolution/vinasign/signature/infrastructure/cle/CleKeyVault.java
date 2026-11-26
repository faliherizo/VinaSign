package ca.vinaitsolution.vinasign.signature.infrastructure.cle;

import ca.vinaitsolution.vinasign.signature.application.port.CleDeSignature;
import ca.vinaitsolution.vinasign.signature.domain.AlgorithmeSignature;
import com.azure.security.keyvault.keys.cryptography.CryptographyClient;
import com.azure.security.keyvault.keys.cryptography.models.SignatureAlgorithm;
import com.azure.security.keyvault.keys.models.KeyType;
import com.azure.security.keyvault.keys.models.KeyVaultKey;
import java.math.BigInteger;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.List;

/**
 * Clé de cachet dans Azure Key Vault Premium (VS-031) : la signature se fait dans le HSM, la clé n'en sort jamais. Une
 * clé exportable est refusée au démarrage : elle contredirait la promesse faite aux organisations.
 */
public class CleKeyVault implements CleDeSignature {

    private final CryptographyClient crypto;
    private final List<X509Certificate> chaine;
    private final AlgorithmeSignature algorithme;

    public CleKeyVault(CryptographyClient crypto, List<X509Certificate> chaine) {
        KeyVaultKey cle = crypto.getKey();
        if (Boolean.TRUE.equals(cle.getProperties().isExportable())) {
            throw new IllegalStateException("Clé de cachet exportable refusée : créer une clé de HSM non exportable.");
        }
        if (chaine == null || chaine.isEmpty()) {
            throw new IllegalStateException("Certificat du cachet absent.");
        }
        this.crypto = crypto;
        this.chaine = List.copyOf(chaine);
        this.algorithme = algorithme(cle.getKeyType());
    }

    static AlgorithmeSignature algorithme(KeyType type) {
        if (KeyType.RSA.equals(type) || KeyType.RSA_HSM.equals(type)) {
            return AlgorithmeSignature.RSA_SHA256;
        }
        if (KeyType.EC.equals(type) || KeyType.EC_HSM.equals(type)) {
            return AlgorithmeSignature.ECDSA_SHA256;
        }
        throw new IllegalStateException("Type de clé de cachet non pris en charge : RSA ou EC P-256.");
    }

    @Override
    public byte[] signerEmpreinte(byte[] sha256) {
        Empreintes.exigerSha256(sha256);
        if (algorithme == AlgorithmeSignature.RSA_SHA256) {
            return crypto.sign(SignatureAlgorithm.RS256, sha256).getSignature();
        }
        return der(crypto.sign(SignatureAlgorithm.ES256, sha256).getSignature());
    }

    /**
     * Key Vault rend la signature ECDSA brute (R ‖ S) ; le CMS l'attend encodée DER : SEQUENCE de deux INTEGER. Pour P-256,
     * chaque longueur tient sur un octet (forme courte).
     */
    static byte[] der(byte[] brute) {
        int moitie = brute.length / 2;
        byte[] r = new BigInteger(1, Arrays.copyOfRange(brute, 0, moitie)).toByteArray();
        byte[] s = new BigInteger(1, Arrays.copyOfRange(brute, moitie, brute.length)).toByteArray();
        java.io.ByteArrayOutputStream o = new java.io.ByteArrayOutputStream();
        o.write(0x30);
        o.write(4 + r.length + s.length);
        for (byte[] entier : new byte[][] {r, s}) {
            o.write(0x02);
            o.write(entier.length);
            o.write(entier, 0, entier.length);
        }
        return o.toByteArray();
    }

    @Override
    public AlgorithmeSignature algorithme() {
        return algorithme;
    }

    @Override
    public List<X509Certificate> chaine() {
        return chaine;
    }
}
