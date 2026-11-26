package ca.vinaitsolution.vinasign.signature.infrastructure;

import ca.vinaitsolution.vinasign.commun.application.port.ArchivageLongTerme;
import ca.vinaitsolution.vinasign.commun.application.port.ScellementDocuments;
import java.time.Instant;
import java.util.Optional;
import ca.vinaitsolution.vinasign.signature.application.port.Cachet;
import org.springframework.stereotype.Component;

/** Le scellement et l'archivage à long terme (VS-132) demandés par les autres contextes passent par le cachet de la plateforme. */
@Component
class ScellementParCachet implements ScellementDocuments, ArchivageLongTerme {

    private final Cachet cachet;

    ScellementParCachet(Cachet cachet) {
        this.cachet = cachet;
    }

    @Override
    public byte[] scellerPdf(byte[] pdf, String motif) {
        return cachet.scellerPdf(pdf, motif);
    }

    @Override
    public byte[] signerEmpreinte(byte[] sha256) {
        return cachet.signerEmpreinte(sha256);
    }

    @Override
    public Optional<Instant> echeance(byte[] pdf) {
        return cachet.echeanceHorodatage(pdf);
    }

    @Override
    public byte[] renouveler(byte[] pdf) {
        return cachet.renouvelerHorodatage(pdf);
    }
}
