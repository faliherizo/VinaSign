package ca.vinaitsolution.vinasign.commun.application.port;

import java.time.Instant;
import java.util.Optional;

/**
 * Validité à long terme d'un PDF scellé (VS-132, PAdES-B-LTA, ETSI EN 319 142-1 § 5.5) : avant que le certificat de
 * l'autorité du dernier horodatage n'expire, les données de validation sont complétées et un nouvel horodatage de document
 * couvre le tout. Les révisions précédentes restent intactes (mise à jour incrémentale).
 */
public interface ArchivageLongTerme {

    /** Fin de validité du certificat qui a signé le dernier horodatage de document ; vide : aucun horodatage. */
    Optional<Instant> echeance(byte[] pdf);

    /** PDF augmenté des données de validation à jour et d'un nouvel horodatage de document. */
    byte[] renouveler(byte[] pdf);
}
