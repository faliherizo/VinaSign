package ca.vinaitsolution.vinasign.signature.infrastructure;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import ca.vinaitsolution.vinasign.signature.application.port.Cachet;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Les autres contextes scellent, signent et renouvellent l'archive (VS-132) par le seul cachet de la plateforme. */
class ScellementParCachetTest {

    @Test
    void chaque_operation_passe_par_le_cachet() {
        Cachet cachet = mock(Cachet.class);
        ScellementParCachet s = new ScellementParCachet(cachet);
        byte[] pdf = {1};
        when(cachet.scellerPdf(pdf, "m")).thenReturn(new byte[] {2});
        when(cachet.signerEmpreinte(pdf)).thenReturn(new byte[] {3});
        when(cachet.renouvelerHorodatage(pdf)).thenReturn(new byte[] {4});
        when(cachet.echeanceHorodatage(pdf)).thenReturn(Optional.of(Instant.EPOCH));
        assertArrayEquals(new byte[] {2}, s.scellerPdf(pdf, "m"));
        assertArrayEquals(new byte[] {3}, s.signerEmpreinte(pdf));
        assertArrayEquals(new byte[] {4}, s.renouveler(pdf));
        assertEquals(Optional.of(Instant.EPOCH), s.echeance(pdf));
    }
}
