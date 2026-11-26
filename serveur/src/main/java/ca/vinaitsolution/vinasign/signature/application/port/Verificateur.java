package ca.vinaitsolution.vinasign.signature.application.port;

import ca.vinaitsolution.vinasign.signature.application.dto.RapportVerification;

/** Vérification indépendante (VS-036) : rien de ce qui est soumis n'est conservé. */
public interface Verificateur {

    RapportVerification pdf(byte[] pdf);

    /** CMS détaché d'une empreinte SHA-256 : vrai si la signature est valide et porte cette empreinte. */
    RapportVerification empreinte(byte[] sha256, byte[] cms);
}
