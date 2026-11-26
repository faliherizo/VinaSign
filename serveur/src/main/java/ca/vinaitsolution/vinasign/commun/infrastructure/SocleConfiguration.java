package ca.vinaitsolution.vinasign.commun.infrastructure;

import ca.vinaitsolution.vinasign.commun.application.port.Chiffrement;
import java.security.SecureRandom;
import org.springframework.beans.factory.annotation.Value;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Horloge et aléa injectés partout : aucun appel direct à l'heure système ni au hasard dans le métier. */
@Configuration
public class SocleConfiguration {

    @Bean
    Clock horloge() {
        return Clock.systemUTC();
    }

    @Bean
    SecureRandom alea() {
        return new SecureRandom();
    }

    @Bean
    Chiffrement chiffrement(@Value("${vinasign.chiffrement.cle:}") String cle, SecureRandom alea) {
        return new ChiffrementAesGcm(cle, alea);
    }
}
