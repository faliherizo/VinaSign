package ca.vinaitsolution.vinasign.session.infrastructure;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import ca.vinaitsolution.vinasign.session.application.RelancerSignataires;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class PlanificationSessionTest {

    @Test
    void l_echeance_declenche_les_relances() {
        RelancerSignataires relances = mock(RelancerSignataires.class);
        new SessionConfiguration.RelancesPlanifiees(relances).relancer();
        verify(relances).executer();
    }

    @Test
    void les_reglages_de_session_ne_revelent_jamais_la_cle_des_codes() {
        var p = new SessionConfiguration.SessionProprietes(Duration.ofDays(30), Duration.ofMinutes(30), 50, Duration.ofMinutes(10), "Q0xFLVNFQ1JFVEUtMjU2",
                Duration.ofMinutes(5), "https://signer.exemple", Duration.ofDays(2), 3);
        assertFalse(p.toString().contains("Q0xFLVNFQ1JFVEUtMjU2"), p.toString());
        assertTrue(p.toString().contains("dureeSession=PT30M"));
    }
}
