package ca.vinaitsolution.vinasign.commun.application.port;

import ca.vinaitsolution.vinasign.commun.domain.Attestation;

/**
 * Vérifie l'assertion par laquelle une application cliente atteste son utilisateur (VS-044) : signée par une clé
 * enregistrée de cette application, émise par elle, destinée aux attestations de VinaSign, de courte durée, à usage
 * unique. Lève {@code AttestationRefusee} sinon.
 */
public interface VerificateurAttestations {

    Attestation verifier(String clientApplication, String assertion);
}
