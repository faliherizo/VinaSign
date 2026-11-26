package ca.vinaitsolution.vinasign.acces.infrastructure.securite;

import ca.vinaitsolution.vinasign.acces.application.port.GenerateurIdentifiantClient;
import ca.vinaitsolution.vinasign.acces.domain.IdentifiantClient;
import java.security.SecureRandom;
import java.util.Base64;
import org.springframework.stereotype.Component;

/** {@code vs-} suivi de 128 bits aléatoires (base64url) : non prévisible, sans information sur l'organisation. */
@Component
class GenerateurIdentifiantClientAleatoire implements GenerateurIdentifiantClient {

    private final SecureRandom alea;

    GenerateurIdentifiantClientAleatoire(SecureRandom alea) {
        this.alea = alea;
    }

    @Override
    public IdentifiantClient suivant() {
        byte[] octets = new byte[16];
        alea.nextBytes(octets);
        return new IdentifiantClient("vs-" + Base64.getUrlEncoder().withoutPadding().encodeToString(octets));
    }
}
