package ca.vinaitsolution.vinasign.signature.application.port;

import ca.vinaitsolution.vinasign.signature.domain.AlgorithmeSignature;
import java.security.cert.X509Certificate;
import java.util.List;

/**
 * Clé de cachet d'une organisation (VS-031), dans un HSM en exploitation. Elle signe une empreinte, jamais un document :
 * la clé privée ne quitte pas le HSM et ce port n'offre aucun moyen de l'obtenir.
 */
public interface CleDeSignature {

    /**
     * Signe une empreinte SHA-256 (32 octets) ; rend la signature brute (RSA PKCS#1 v1.5, ou ECDSA encodée DER).
     * Une empreinte d'une autre taille est refusée.
     */
    byte[] signerEmpreinte(byte[] sha256);

    AlgorithmeSignature algorithme();

    /** Certificat du cachet en tête, puis les autorités intermédiaires. */
    List<X509Certificate> chaine();
}
