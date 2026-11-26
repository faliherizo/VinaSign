package ca.vinaitsolution.vinasign.signature.infrastructure;

import static org.junit.jupiter.api.Assertions.*;

import ca.vinaitsolution.vinasign.signature.application.port.Cachet;
import org.junit.jupiter.api.Test;

/** Sans cachet configuré, rien n'est signé ni scellé, et aucun certificat n'est reconnu comme celui de la plateforme. */
class CachetIndisponibleTest {

    @Test
    void sans_cachet_aucune_signature_n_est_produite() {
        Cachet c = new CachetIndisponible();
        for (Runnable r : new Runnable[] {() -> c.signerDetache(new byte[1]), () -> c.signerEmpreinte(new byte[32]), () -> c.scellerPdf(new byte[1], "m"),
                c::niveauPades, c::titulaire, () -> c.renouvelerHorodatage(new byte[1]), () -> c.echeanceHorodatage(new byte[1])}) {
            assertTrue(assertThrows(IllegalStateException.class, r::run).getMessage().contains("vinasign.cachet.fournisseur"));
        }
        assertFalse(c.reconnait(null));
    }
}
