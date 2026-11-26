package ca.vinaitsolution.vinasign.commun.infrastructure.identifiant;

import ca.vinaitsolution.vinasign.commun.application.port.GenerateurIdentifiant;
import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.time.Clock;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * UUID version 7 (RFC 9562) : ordonnés dans le temps, ce qui garde les index B-tree compacts, et imprévisibles grâce à
 * 74 bits aléatoires, ce qui interdit l'énumération des ressources. L'horloge et l'aléa sont injectés.
 */
@Component
public class GenerateurUuidV7 implements GenerateurIdentifiant {

    private final Clock horloge;
    private final SecureRandom alea;

    public GenerateurUuidV7(Clock horloge, SecureRandom alea) {
        this.horloge = horloge;
        this.alea = alea;
    }

    @Override
    public UUID suivant() {
        byte[] octets = new byte[16];
        alea.nextBytes(octets);
        long millis = horloge.millis();
        for (int i = 0; i < 6; i++) {
            octets[i] = (byte) (millis >>> (40 - 8 * i));
        }
        octets[6] = (byte) ((octets[6] & 0x0F) | 0x70);
        octets[8] = (byte) ((octets[8] & 0x3F) | 0x80);
        ByteBuffer b = ByteBuffer.wrap(octets);
        return new UUID(b.getLong(), b.getLong());
    }
}
