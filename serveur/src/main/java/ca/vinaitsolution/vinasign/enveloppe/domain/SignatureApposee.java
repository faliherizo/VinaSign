package ca.vinaitsolution.vinasign.enveloppe.domain;

import java.util.UUID;

/** Signature recueillie d'un signataire : image dessinée (clé de stockage) ou nom tapé. */
public record SignatureApposee(UUID signataire, String cleImage, String nomSaisi) {

    public SignatureApposee {
        if (signataire == null || (cleImage == null) == (nomSaisi == null)) {
            throw new IllegalArgumentException("Une signature est soit une image, soit un nom tapé.");
        }
    }

    public boolean dessinee() {
        return cleImage != null;
    }
}
