package ca.vinaitsolution.vinasign.acces;

import static org.junit.jupiter.api.Assertions.*;

import ca.vinaitsolution.vinasign.acces.application.EnregistrerApplication;
import ca.vinaitsolution.vinasign.acces.application.GererApplication;
import ca.vinaitsolution.vinasign.acces.domain.ApplicationCliente;
import ca.vinaitsolution.vinasign.acces.domain.ApplicationIntrouvable;
import ca.vinaitsolution.vinasign.acces.domain.ClesPubliques;
import ca.vinaitsolution.vinasign.acces.domain.IdentifiantClient;
import ca.vinaitsolution.vinasign.acces.domain.OrganisationRequise;
import ca.vinaitsolution.vinasign.acces.domain.TransitionApplicationInterdite;
import ca.vinaitsolution.vinasign.commun.domain.Acteur;
import ca.vinaitsolution.vinasign.commun.domain.EvenementAudit;
import ca.vinaitsolution.vinasign.commun.domain.IdentifiantOrganisation;
import ca.vinaitsolution.vinasign.support.ApplicationsClientesEnMemoire;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class GererApplicationTest {

    private static final Instant MAINTENANT = Instant.parse("2026-11-22T17:00:00Z");
    private final IdentifiantOrganisation org = new IdentifiantOrganisation(UUID.randomUUID());
    private IdentifiantOrganisation courante = org;
    private final List<EvenementAudit> journal = new ArrayList<>();
    private final ApplicationsClientesEnMemoire registre = new ApplicationsClientesEnMemoire(() -> Optional.ofNullable(courante));
    private final GererApplication gestion = new GererApplication(registre, jwks -> new ClesPubliques(jwks, Set.of("e-" + jwks)),
            () -> Optional.ofNullable(courante), new EnregistrerApplication.Collaborateurs(() -> fail("aucun nouveau client"),
            UUID::randomUUID, () -> new Acteur("admin-org"), journal::add, Clock.fixed(MAINTENANT, ZoneOffset.UTC)));
    private final IdentifiantClient client = new IdentifiantClient("vs-gestion-1");

    @BeforeEach
    void enregistrer() {
        registre.ajouter(ApplicationCliente.enregistrer(UUID.randomUUID(), org, client, "App", "production",
                new ClesPubliques("ancienne", Set.of("e-ancienne")), Set.of(), Set.of()), new ClesPubliques("ancienne", Set.of("e-ancienne")));
    }

    @Test
    void la_revocation_est_datee_enregistree_et_consignee() {
        assertEquals("revoquee", gestion.revoquer(client).statut());
        ApplicationCliente relue = registre.deLOrganisation(client).orElseThrow();
        assertEquals(MAINTENANT, relue.revoqueLe());
        assertTrue(registre.activeParClient(client).isEmpty());
        assertEquals("application-revoquee", journal.get(0).action());
        assertEquals(client.valeur(), journal.get(0).cible());
        assertThrows(TransitionApplicationInterdite.class, () -> gestion.revoquer(client), "une seule révocation");
        assertThrows(TransitionApplicationInterdite.class, () -> gestion.renouvelerCles(client, "nouvelle"),
                "une application révoquée ne change plus de clés");
        assertEquals(1, journal.size());
    }

    @Test
    void la_rotation_remplace_les_cles_et_est_consignee() {
        gestion.renouvelerCles(client, "ancienne+nouvelle");
        assertEquals("ancienne+nouvelle", registre.deLOrganisation(client).orElseThrow().clesPubliques());
        assertEquals("cles-application-renouvelees", journal.get(0).action());
        assertEquals(new Acteur("admin-org"), journal.get(0).acteur());
    }

    @Test
    void une_application_d_une_autre_organisation_est_introuvable_et_sans_organisation_rien_ne_se_fait() {
        courante = new IdentifiantOrganisation(UUID.randomUUID());
        assertThrows(ApplicationIntrouvable.class, () -> gestion.revoquer(client));
        courante = null;
        assertThrows(OrganisationRequise.class, () -> gestion.renouvelerCles(client, "x"));
        assertTrue(journal.isEmpty());
    }
}
