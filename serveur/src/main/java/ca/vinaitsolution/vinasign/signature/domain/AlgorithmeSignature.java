package ca.vinaitsolution.vinasign.signature.domain;

/**
 * Algorithme d'une clé de cachet : il décide de la forme de la signature brute et de l'identifiant d'algorithme du CMS.
 * Invariant technique (VS-031), non une liste métier.
 */
public enum AlgorithmeSignature {
    /** RSA PKCS#1 v1.5 sur SHA-256. */
    RSA_SHA256("SHA256withRSA"),
    /** ECDSA P-256 sur SHA-256, signature DER. */
    ECDSA_SHA256("SHA256withECDSA");

    private final String nomJca;

    AlgorithmeSignature(String nomJca) {
        this.nomJca = nomJca;
    }

    public String nomJca() {
        return nomJca;
    }
}
