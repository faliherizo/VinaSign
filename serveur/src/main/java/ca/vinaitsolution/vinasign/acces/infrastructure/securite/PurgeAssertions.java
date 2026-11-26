package ca.vinaitsolution.vinasign.acces.infrastructure.securite;

import ca.vinaitsolution.vinasign.acces.application.port.AssertionsConsommees;
import java.time.Clock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Oublie périodiquement les assertions expirées : elles ne peuvent plus être rejouées. */
@Component
class PurgeAssertions {

    private final AssertionsConsommees assertions;
    private final Clock horloge;

    PurgeAssertions(AssertionsConsommees assertions, Clock horloge) {
        this.assertions = assertions;
        this.horloge = horloge;
    }

    @Scheduled(fixedDelayString = "${vinasign.jetons.purge-assertions:PT10M}")
    int purger() {
        return assertions.purger(horloge.instant());
    }
}
