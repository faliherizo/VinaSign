package ca.vinaitsolution.vinasign.spring;

import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;

/** Lecture d'une clé privée PKCS#8 (PEM) RSA ou EC. */
final class ClePem {

    private ClePem() {
    }

    static PrivateKey lire(String pem) {
        if (pem == null || !pem.contains("BEGIN PRIVATE KEY")) {
            throw new IllegalArgumentException("vinasign.cle-privee : clé PKCS#8 (« BEGIN PRIVATE KEY ») attendue.");
        }
        byte[] der = Base64.getMimeDecoder().decode(pem.replaceAll("-----(BEGIN|END) PRIVATE KEY-----", "").strip());
        for (String algorithme : new String[] {"RSA", "EC"}) {
            try {
                return KeyFactory.getInstance(algorithme).generatePrivate(new PKCS8EncodedKeySpec(der));
            } catch (GeneralSecurityException e) {
                // Essai de l'algorithme suivant : le PEM PKCS#8 ne le nomme pas dans son en-tête.
            }
        }
        throw new IllegalArgumentException("vinasign.cle-privee : ni RSA ni EC.");
    }
}
