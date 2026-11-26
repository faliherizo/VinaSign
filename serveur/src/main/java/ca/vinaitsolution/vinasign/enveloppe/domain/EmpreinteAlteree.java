package ca.vinaitsolution.vinasign.enveloppe.domain;

/**
 * Le contenu lu ne correspond plus à l'empreinte enregistrée (VS-022) : altération du stockage. Ce n'est pas une erreur
 * de l'appelant ; elle arrête l'opération et doit être examinée.
 */
public class EmpreinteAlteree extends IllegalStateException {

    public EmpreinteAlteree() {
        super("Le document stocké ne correspond plus à son empreinte.");
    }
}
