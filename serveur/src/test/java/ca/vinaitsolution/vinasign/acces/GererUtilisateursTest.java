package ca.vinaitsolution.vinasign.acces;

import static org.junit.jupiter.api.Assertions.*;

import ca.vinaitsolution.vinasign.acces.application.EnregistrerApplication;
import ca.vinaitsolution.vinasign.acces.application.GererUtilisateurs;
import ca.vinaitsolution.vinasign.acces.application.port.UtilisateursPortail;
import ca.vinaitsolution.vinasign.acces.domain.OrganisationRequise;
import ca.vinaitsolution.vinasign.commun.domain.Acteur;
import ca.vinaitsolution.vinasign.referentiel.application.ExigerValeurReferentiel;
import ca.vinaitsolution.vinasign.support.ReferentielsEnMemoire;
import java.time.Clock;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class GererUtilisateursTest {

    private final GererUtilisateurs gestion = new GererUtilisateurs(Mockito.mock(UtilisateursPortail.class),
            new ExigerValeurReferentiel(new ReferentielsEnMemoire()), () -> Optional.of(new ca.vinaitsolution.vinasign.commun.domain.IdentifiantOrganisation(UUID.randomUUID())),
            o -> false, new EnregistrerApplication.Collaborateurs(() -> fail(), UUID::randomUUID, () -> new Acteur("a"), e -> fail(),
            Clock.systemUTC()));

    @Test
    void une_invitation_sans_role_est_refusee_avant_toute_ecriture() {
        assertThrows(IllegalArgumentException.class, () -> gestion.inviter("u@exemple.ca", Set.of()));
        assertThrows(IllegalArgumentException.class, () -> gestion.inviter("u@exemple.ca", null));
    }

    @Test
    void sans_organisation_on_ne_liste_ni_ne_modifie() {
        GererUtilisateurs sans = new GererUtilisateurs(Mockito.mock(UtilisateursPortail.class), null, Optional::empty, o -> false, null);
        assertThrows(OrganisationRequise.class, sans::lister);
        assertThrows(OrganisationRequise.class, () -> sans.desactiver(UUID.randomUUID()));
    }
}
