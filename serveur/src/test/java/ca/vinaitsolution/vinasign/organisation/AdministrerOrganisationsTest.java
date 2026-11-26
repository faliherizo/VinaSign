package ca.vinaitsolution.vinasign.organisation;

import static ca.vinaitsolution.vinasign.support.Fabrique.type;
import static ca.vinaitsolution.vinasign.support.Fabrique.valeur;
import static org.junit.jupiter.api.Assertions.*;

import ca.vinaitsolution.vinasign.commun.domain.Acteur;
import ca.vinaitsolution.vinasign.commun.domain.EvenementAudit;
import ca.vinaitsolution.vinasign.commun.domain.IdentifiantOrganisation;
import ca.vinaitsolution.vinasign.organisation.application.AdministrerOrganisations;
import ca.vinaitsolution.vinasign.organisation.application.dto.VueOrganisation;
import ca.vinaitsolution.vinasign.organisation.domain.OrganisationIntrouvable;
import ca.vinaitsolution.vinasign.organisation.domain.TransitionOrganisationInterdite;
import ca.vinaitsolution.vinasign.referentiel.domain.ValeurReferentielInconnue;
import ca.vinaitsolution.vinasign.support.OrganisationsEnMemoire;
import ca.vinaitsolution.vinasign.support.ReferentielsEnMemoire;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AdministrerOrganisationsTest {

    private static final Instant MAINTENANT = Instant.parse("2026-11-22T15:00:00Z");
    private final List<EvenementAudit> journal = new ArrayList<>();
    private final OrganisationsEnMemoire organisations = new OrganisationsEnMemoire();
    private final ReferentielsEnMemoire referentiels = new ReferentielsEnMemoire()
            .avec(type("plan", valeur("petit", 1, true), valeur("moyen", 2, true), valeur("retire", 3, false)));
    private final AdministrerOrganisations administration = new AdministrerOrganisations(organisations, referentiels, journal::add,
            () -> new Acteur("admin-plateforme-1"), UUID::randomUUID, Clock.fixed(MAINTENANT, ZoneOffset.UTC));

    @Test
    void chaque_changement_est_consigne_avec_son_acteur_son_action_et_sa_date() {
        VueOrganisation creee = administration.creer("Clinique");
        IdentifiantOrganisation id = new IdentifiantOrganisation(creee.id());
        administration.suspendre(id);
        VueOrganisation reactivee = administration.reactiver(id);

        assertEquals("active", reactivee.statut());
        assertEquals(List.of("organisation-creee", "organisation-suspendue", "organisation-reactivee"),
                journal.stream().map(EvenementAudit::action).toList());
        for (EvenementAudit e : journal) {
            assertEquals(id, e.organisation());
            assertEquals(new Acteur("admin-plateforme-1"), e.acteur());
            assertEquals(creee.id().toString(), e.cible());
            assertEquals(MAINTENANT, e.survenuLe());
        }
        assertEquals(3, journal.stream().map(EvenementAudit::id).distinct().count(), "chaque entrée a son identifiant");
    }

    @Test
    void la_consultation_rend_l_etat_courant() {
        IdentifiantOrganisation id = new IdentifiantOrganisation(administration.creer("Org").id());
        administration.suspendre(id);
        assertEquals("suspendue", administration.consulter(id).statut());
    }

    @Test
    void une_organisation_inconnue_est_introuvable_et_rien_n_est_consigne() {
        IdentifiantOrganisation inconnue = new IdentifiantOrganisation(UUID.randomUUID());
        assertThrows(OrganisationIntrouvable.class, () -> administration.consulter(inconnue));
        assertThrows(OrganisationIntrouvable.class, () -> administration.suspendre(inconnue));
        assertTrue(journal.isEmpty());
    }

    @Test
    void une_transition_refusee_ne_consigne_rien() {
        IdentifiantOrganisation id = new IdentifiantOrganisation(administration.creer("Org").id());
        assertThrows(TransitionOrganisationInterdite.class, () -> administration.reactiver(id));
        assertEquals(1, journal.size());
    }

    @Test
    void un_evenement_d_audit_incomplet_est_refuse() {
        IdentifiantOrganisation id = new IdentifiantOrganisation(UUID.randomUUID());
        Acteur a = new Acteur("x");
        assertThrows(IllegalArgumentException.class, () -> new EvenementAudit(null, id, a, "act", null, MAINTENANT));
        assertThrows(IllegalArgumentException.class, () -> new EvenementAudit(UUID.randomUUID(), null, a, "act", null, MAINTENANT));
        assertThrows(IllegalArgumentException.class, () -> new EvenementAudit(UUID.randomUUID(), id, null, "act", null, MAINTENANT));
        assertThrows(IllegalArgumentException.class, () -> new EvenementAudit(UUID.randomUUID(), id, a, null, null, MAINTENANT));
        assertThrows(IllegalArgumentException.class, () -> new EvenementAudit(UUID.randomUUID(), id, a, " ", null, MAINTENANT));
        assertThrows(IllegalArgumentException.class, () -> new EvenementAudit(UUID.randomUUID(), id, a, "act", null, null));
    }

    @Test
    void une_reference_d_acteur_vide_ou_demesuree_est_refusee() {
        assertThrows(IllegalArgumentException.class, () -> new Acteur(null));
        assertThrows(IllegalArgumentException.class, () -> new Acteur(" "));
        assertThrows(IllegalArgumentException.class, () -> new Acteur("x".repeat(201)));
    }

    @Test
    void sans_plan_marque_par_defaut_la_premiere_valeur_active_est_attribuee() {
        assertEquals("petit", administration.creer("Org").plan());
    }

    @Test
    void le_plan_marque_par_defaut_est_attribue_a_la_creation() {
        ReferentielsEnMemoire marque = new ReferentielsEnMemoire().avec(type("plan", valeur("petit", 1, true),
                new ca.vinaitsolution.vinasign.referentiel.domain.ValeurReferentiel(new ca.vinaitsolution.vinasign.referentiel.domain.CodeReferentiel("moyen"),
                        ca.vinaitsolution.vinasign.commun.domain.Libelles.de(java.util.Map.of("fr", "Moyen")), 2,
                        java.util.Map.of("parDefaut", "true"), true)));
        AdministrerOrganisations avecDefaut = new AdministrerOrganisations(organisations, marque, journal::add, () -> new Acteur("a"),
                UUID::randomUUID, Clock.fixed(MAINTENANT, ZoneOffset.UTC));
        assertEquals("moyen", avecDefaut.creer("Org").plan());
    }

    @Test
    void sans_plan_actif_aucune_organisation_ne_peut_etre_creee() {
        AdministrerOrganisations sansPlan = new AdministrerOrganisations(organisations, new ReferentielsEnMemoire(), journal::add,
                () -> new Acteur("a"), UUID::randomUUID, Clock.fixed(MAINTENANT, ZoneOffset.UTC));
        assertThrows(IllegalStateException.class, () -> sansPlan.creer("Org"));
    }

    @Test
    void le_plan_change_seulement_pour_une_valeur_active_et_c_est_consigne() {
        IdentifiantOrganisation id = new IdentifiantOrganisation(administration.creer("Org").id());
        assertEquals("moyen", administration.changerPlan(id, "moyen").plan());
        assertEquals("plan-organisation-change", journal.get(journal.size() - 1).action());
        assertThrows(ValeurReferentielInconnue.class, () -> administration.changerPlan(id, "retire"));
        assertThrows(ValeurReferentielInconnue.class, () -> administration.changerPlan(id, "inexistant"));
    }
}
