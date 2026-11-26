package ca.vinaitsolution.vinasign.organisation;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import ca.vinaitsolution.vinasign.organisation.application.AdministrerOrganisations;
import ca.vinaitsolution.vinasign.organisation.application.InscrireOrganisation;
import ca.vinaitsolution.vinasign.organisation.application.port.Inscriptions;
import ca.vinaitsolution.vinasign.organisation.domain.InscriptionFermee;
import ca.vinaitsolution.vinasign.organisation.domain.InscriptionIntrouvable;
import java.time.Clock;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** VS-122 — fermée par défaut : aucune demande n'est reçue ; un jeton vide ou démesuré ne touche pas la base. */
class InscriptionFermeeTest {

    @Test
    void sans_politique_l_inscription_est_fermee() {
        Inscriptions inscriptions = mock(Inscriptions.class);
        InscrireOrganisation i = new InscrireOrganisation(new InscrireOrganisation.Ports(inscriptions, null, null, null),
                new InscrireOrganisation.Outils(UUID::randomUUID, new java.security.SecureRandom(), Clock.systemUTC()), mock(AdministrerOrganisations.class),
                new InscrireOrganisation.Reglages(null, Duration.ofHours(24), "https://portail"));
        assertThrows(InscriptionFermee.class, () -> i.demander("Nord", "a@exemple.ca", null, "fr"));
        assertThrows(InscriptionIntrouvable.class, () -> i.consulter(null));
        assertThrows(InscriptionIntrouvable.class, () -> i.consulter(" "));
        assertThrows(InscriptionIntrouvable.class, () -> i.consulter("x".repeat(101)));
        verifyNoInteractions(inscriptions);
    }
}
