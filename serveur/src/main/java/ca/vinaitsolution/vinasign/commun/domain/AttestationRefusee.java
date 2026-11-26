package ca.vinaitsolution.vinasign.commun.domain;

/** Attestation d'utilisateur refusée (VS-044) ; le motif est rendu à l'application, seule à appeler. */
public class AttestationRefusee extends ErreurMetier {

    public AttestationRefusee(String detail) {
        super(Nature.INTERDIT, "attestation-refusee", detail);
    }
}
