package ca.vinaitsolution.vinasign.session.application;

import ca.vinaitsolution.vinasign.commun.application.port.GenerateurIdentifiant;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.util.Base64;

/** Jetons opaques, horloge et réglages partagés par les cas d'utilisation de la session. */
public record ServicesSession(GenerateurIdentifiant identifiants, SecureRandom alea, Clock horloge, Reglages reglages) {

    /** Durée de vie d'un lien et d'une session, nombre d'ouvertures permises d'un lien. */
    public record Reglages(Duration dureeLien, Duration dureeSession, int ouverturesMax) {
    }

    /** Jeton de 256 bits, encodé base64url. */
    public String nouveauJeton() {
        byte[] octets = new byte[32];
        alea.nextBytes(octets);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(octets);
    }

    /** Seule l'empreinte d'un jeton est conservée. */
    public static String empreinte(String jeton) {
        return ca.vinaitsolution.vinasign.commun.domain.Condensats.sha256Hex(jeton);
    }
}
