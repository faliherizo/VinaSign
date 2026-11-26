package ca.vinaitsolution.vinasign.session.domain;

import java.util.UUID;

/** Lien retrouvé par son jeton, avec l'organisation de son enveloppe : de quoi poser le contexte d'accès du signataire. */
public record LienResolu(LienSignature lien, UUID organisation) {

    public LienResolu {
        if (lien == null || organisation == null) {
            throw new IllegalArgumentException("Lien résolu incomplet.");
        }
    }
}
