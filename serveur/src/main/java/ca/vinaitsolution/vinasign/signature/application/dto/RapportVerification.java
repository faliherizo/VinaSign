package ca.vinaitsolution.vinasign.signature.application.dto;

import java.time.Instant;
import java.util.List;

/**
 * Rapport de vérification (VS-036).
 *
 * @param integre  vrai si chaque signature est valide et que la dernière révision couvre tout le fichier
 * @param niveau   niveau ETSI atteint (PAdES-B-B, PAdES-B-T, PAdES-B-LTA, CAdES-B-B, CAdES-B-T), vide si aucune signature
 */
public record RapportVerification(boolean integre, String niveau, List<SignatureVerifiee> signatures) {

    /** @param horodatage heure attestée par un jeton RFC 3161, s'il y en a un */
    public record SignatureVerifiee(String signataire, boolean valide, Instant horodatage, boolean horodatageDeDocument) {
    }
}
