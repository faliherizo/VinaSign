package ca.vinaitsolution.vinasign.signature.infrastructure;

import ca.vinaitsolution.vinasign.signature.application.port.Cachet;
import java.security.cert.X509Certificate;

/** Aucun cachet configuré : tout scellement échoue explicitement, rien n'est simulé. */
class CachetIndisponible implements Cachet {

    private static IllegalStateException indisponible() {
        return new IllegalStateException("Aucun cachet configuré (vinasign.cachet.fournisseur).");
    }

    @Override
    public byte[] signerDetache(byte[] contenu) {
        throw indisponible();
    }

    @Override
    public byte[] signerEmpreinte(byte[] sha256) {
        throw indisponible();
    }

    @Override
    public byte[] scellerPdf(byte[] pdf, String motif) {
        throw indisponible();
    }

    @Override
    public byte[] renouvelerHorodatage(byte[] pdf) {
        throw indisponible();
    }

    @Override
    public java.util.Optional<java.time.Instant> echeanceHorodatage(byte[] pdf) {
        throw indisponible();
    }

    @Override
    public String niveauPades() {
        throw indisponible();
    }

    @Override
    public boolean reconnait(X509Certificate certificat) {
        return false;
    }

    @Override
    public String titulaire() {
        throw indisponible();
    }
}
