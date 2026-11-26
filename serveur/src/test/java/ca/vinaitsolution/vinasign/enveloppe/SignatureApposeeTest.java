package ca.vinaitsolution.vinasign.enveloppe;

import static org.junit.jupiter.api.Assertions.*;

import ca.vinaitsolution.vinasign.enveloppe.domain.SignatureApposee;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SignatureApposeeTest {

    @Test
    void une_signature_est_soit_dessinee_soit_tapee() {
        UUID s = UUID.randomUUID();
        assertTrue(new SignatureApposee(s, "cle.png", null).dessinee());
        assertFalse(new SignatureApposee(s, null, "Bruno Roy").dessinee());
        assertThrows(IllegalArgumentException.class, () -> new SignatureApposee(s, null, null));
        assertThrows(IllegalArgumentException.class, () -> new SignatureApposee(s, "cle.png", "Bruno"));
        assertThrows(IllegalArgumentException.class, () -> new SignatureApposee(null, "cle.png", null));
    }
}
