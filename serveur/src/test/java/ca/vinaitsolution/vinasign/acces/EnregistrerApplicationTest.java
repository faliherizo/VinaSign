package ca.vinaitsolution.vinasign.acces;

import static ca.vinaitsolution.vinasign.support.Fabrique.type;
import static ca.vinaitsolution.vinasign.support.Fabrique.valeur;
import static org.junit.jupiter.api.Assertions.*;

import ca.vinaitsolution.vinasign.acces.application.EnregistrerApplication;
import ca.vinaitsolution.vinasign.acces.application.EnregistrerApplication.Demande;
import ca.vinaitsolution.vinasign.acces.application.dto.VueApplication;
import ca.vinaitsolution.vinasign.acces.domain.ClesPubliques;
import ca.vinaitsolution.vinasign.acces.domain.ClesPubliquesInvalides;
import ca.vinaitsolution.vinasign.acces.domain.IdentifiantClient;
import ca.vinaitsolution.vinasign.acces.domain.OrganisationRequise;
import ca.vinaitsolution.vinasign.commun.domain.Acteur;
import ca.vinaitsolution.vinasign.commun.domain.EvenementAudit;
import ca.vinaitsolution.vinasign.commun.domain.IdentifiantOrganisation;
import ca.vinaitsolution.vinasign.referentiel.application.ExigerValeurReferentiel;
import ca.vinaitsolution.vinasign.referentiel.domain.ValeurReferentielInconnue;
import ca.vinaitsolution.vinasign.support.ApplicationsClientesEnMemoire;
import ca.vinaitsolution.vinasign.support.ReferentielsEnMemoire;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class EnregistrerApplicationTest {

    private static final Instant MAINTENANT = Instant.parse("2026-11-22T16:00:00Z");
    private final IdentifiantOrganisation org = new IdentifiantOrganisation(UUID.randomUUID());
    private IdentifiantOrganisation courante = org;
    private final List<EvenementAudit> journal = new ArrayList<>();
    private final AtomicInteger numero = new AtomicInteger();
    private final ApplicationsClientesEnMemoire applications = new ApplicationsClientesEnMemoire(() -> Optional.ofNullable(courante));
    private final ExigerValeurReferentiel referentiel = new ExigerValeurReferentiel(new ReferentielsEnMemoire()
            .avec(type("environnement-application", valeur("bac-a-sable", 1, true), valeur("production", 2, true)))
            .avec(type("portee-api", valeur("enveloppes-lire", 1, true), valeur("enveloppes-creer", 2, true),
                    valeur("retiree", 3, false))));
    private final EnregistrerApplication cas = new EnregistrerApplication(applications,
            jwks -> {
                if (jwks.equals("invalide")) throw new ClesPubliquesInvalides("non");
                return new ClesPubliques(jwks, Set.of("empreinte-" + jwks));
            },
            referentiel, () -> Optional.ofNullable(courante),
            new EnregistrerApplication.Collaborateurs(() -> new IdentifiantClient("vs-essai-" + numero.incrementAndGet()), UUID::randomUUID,
                    () -> new Acteur("admin-org"), journal::add, Clock.fixed(MAINTENANT, ZoneOffset.UTC)));

    private static Demande demande(String jwks, Set<String> portees, Set<String> origines) {
        return new Demande("Système RH", "production", jwks, portees, origines);
    }

    @Test
    void l_application_est_enregistree_pour_l_organisation_courante_et_consignee() {
        VueApplication vue = cas.executer(demande("k1", Set.of("enveloppes-lire", "enveloppes-creer"), Set.of("https://RH.exemple.ca")));

        assertEquals("vs-essai-1", vue.clientId());
        assertEquals("production", vue.environnement());
        assertEquals(List.of("enveloppes-creer", "enveloppes-lire"), vue.portees());
        assertEquals(List.of("https://rh.exemple.ca"), vue.origines());
        assertEquals("active", vue.statut());
        assertEquals(org, applications.activeParClient(new IdentifiantClient("vs-essai-1")).orElseThrow().organisation());
        EvenementAudit e = journal.get(0);
        assertEquals("application-enregistree", e.action());
        assertEquals("vs-essai-1", e.cible());
        assertEquals(new Acteur("admin-org"), e.acteur());
        assertEquals(MAINTENANT, e.survenuLe());
        assertEquals(List.of(vue), cas.lister());
    }

    @Test
    void sans_portee_ni_origine_l_application_est_enregistree_sans_droit() {
        VueApplication vue = cas.executer(demande("k2", null, null));
        assertEquals(List.of(), vue.portees());
        assertEquals(List.of(), vue.origines());
    }

    @Test
    void sans_organisation_rien_n_est_enregistre_ni_liste() {
        courante = null;
        assertThrows(OrganisationRequise.class, () -> cas.executer(demande("k3", Set.of(), Set.of())));
        assertThrows(OrganisationRequise.class, cas::lister);
        assertTrue(journal.isEmpty());
    }

    @Test
    void un_environnement_ou_une_portee_hors_referentiel_est_refuse() {
        assertThrows(ValeurReferentielInconnue.class, () -> cas.executer(new Demande("n", "recette", "k", Set.of(), Set.of())));
        assertThrows(ValeurReferentielInconnue.class, () -> cas.executer(demande("k", Set.of("tout-faire"), Set.of())));
        assertThrows(ValeurReferentielInconnue.class, () -> cas.executer(demande("k", Set.of("retiree"), Set.of())),
                "une portée désactivée n'est plus accordable");
        assertTrue(journal.isEmpty());
    }

    @Test
    void une_origine_ou_des_cles_invalides_n_enregistrent_rien() {
        assertThrows(IllegalArgumentException.class, () -> cas.executer(demande("k", Set.of(), Set.of("http://rh.exemple.ca"))));
        assertThrows(ClesPubliquesInvalides.class, () -> cas.executer(demande("invalide", Set.of(), Set.of())));
        assertTrue(cas.lister().isEmpty());
        assertTrue(journal.isEmpty());
    }
}
