package ca.vinaitsolution.vinasign.signature.infrastructure;

import ca.vinaitsolution.vinasign.signature.application.port.CleDeSignature;
import ca.vinaitsolution.vinasign.signature.domain.AlgorithmeSignature;
import java.security.cert.X509Certificate;
import java.util.List;

/** Aucune clé configurée : le serveur démarre, mais tout scellement échoue explicitement plutôt que d'être simulé. */
class CleAbsente implements CleDeSignature {

    @Override
    public byte[] signerEmpreinte(byte[] sha256) {
        throw new IllegalStateException("Aucune clé de cachet configurée (vinasign.cachet.fournisseur).");
    }

    @Override
    public AlgorithmeSignature algorithme() {
        throw new IllegalStateException("Aucune clé de cachet configurée (vinasign.cachet.fournisseur).");
    }

    @Override
    public List<X509Certificate> chaine() {
        return List.of();
    }
}
