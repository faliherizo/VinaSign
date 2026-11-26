package ca.vinaitsolution.vinasign.organisation;

import static org.junit.jupiter.api.Assertions.*;

import ca.vinaitsolution.vinasign.commun.domain.ErreurMetier;
import ca.vinaitsolution.vinasign.commun.domain.IdentifiantOrganisation;
import ca.vinaitsolution.vinasign.organisation.domain.Organisation;
import ca.vinaitsolution.vinasign.organisation.domain.OrganisationIntrouvable;
import ca.vinaitsolution.vinasign.organisation.domain.StatutOrganisation;
import ca.vinaitsolution.vinasign.organisation.domain.TransitionOrganisationInterdite;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class OrganisationDomaineTest {

    private static final IdentifiantOrganisation ID = new IdentifiantOrganisation(UUID.randomUUID());
    private static final Instant T0 = Instant.parse("2026-11-22T15:00:00Z");
    private static final Instant T1 = T0.plusSeconds(60);

    @Test
    void une_organisation_nait_active_avec_un_nom_epure() {
        Organisation o = Organisation.creer(ID, "  Clinique Lévis  ", "plan-essai", T0);
        assertEquals("Clinique Lévis", o.nom());
        assertTrue(o.estActive());
        assertEquals(T0, o.creeLe());
        assertEquals(T0, o.modifieLe());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void un_nom_vide_est_refuse(String nom) {
        assertThrows(IllegalArgumentException.class, () -> Organisation.creer(ID, nom, "plan-essai", T0));
    }

    @Test
    void un_nom_de_201_caracteres_est_refuse_et_200_accepte() {
        assertThrows(IllegalArgumentException.class, () -> Organisation.creer(ID, "x".repeat(201), "plan-essai", T0));
        assertEquals(200, Organisation.creer(ID, "x".repeat(200), "plan-essai", T0).nom().length());
    }

    @Test
    void une_organisation_incomplete_est_refusee() {
        assertThrows(IllegalArgumentException.class, () -> new Organisation(null, "n", StatutOrganisation.ACTIVE, T0, T0, "plan-essai"));
        assertThrows(IllegalArgumentException.class, () -> new Organisation(ID, "n", null, T0, T0, "plan-essai"));
        assertThrows(IllegalArgumentException.class, () -> new Organisation(ID, "n", StatutOrganisation.ACTIVE, null, T0, "plan-essai"));
        assertThrows(IllegalArgumentException.class, () -> new Organisation(ID, "n", StatutOrganisation.ACTIVE, T0, null, "plan-essai"));
    }

    @Test
    void suspendre_puis_reactiver_conserve_l_identite_et_date_le_changement() {
        Organisation suspendue = Organisation.creer(ID, "Org", "plan-essai", T0).suspendre(T1);
        assertEquals(StatutOrganisation.SUSPENDUE, suspendue.statut());
        assertFalse(suspendue.estActive());
        assertEquals(T0, suspendue.creeLe());
        assertEquals(T1, suspendue.modifieLe());
        Organisation reactivee = suspendue.reactiver(T1.plusSeconds(1));
        assertTrue(reactivee.estActive());
        assertEquals(ID, reactivee.id());
        assertEquals(T1.plusSeconds(1), reactivee.modifieLe());
    }

    @Test
    void une_organisation_sans_plan_est_refusee_et_un_changement_de_plan_est_date() {
        assertThrows(IllegalArgumentException.class, () -> Organisation.creer(ID, "Org", null, T0));
        assertThrows(IllegalArgumentException.class, () -> Organisation.creer(ID, "Org", " ", T0));
        Organisation changee = Organisation.creer(ID, "Org", "plan-essai", T0).suspendre(T1).changerPlan("superieur", T1.plusSeconds(1));
        assertEquals("superieur", changee.plan());
        assertEquals(StatutOrganisation.SUSPENDUE, changee.statut(), "le plan ne change pas le statut");
        assertEquals(T1.plusSeconds(1), changee.modifieLe());
        assertEquals("superieur", changee.reactiver(T1.plusSeconds(2)).plan());
    }

    @Test
    void une_transition_repetee_est_un_conflit() {
        Organisation active = Organisation.creer(ID, "Org", "plan-essai", T0);
        TransitionOrganisationInterdite e = assertThrows(TransitionOrganisationInterdite.class, () -> active.reactiver(T1));
        assertEquals(ErreurMetier.Nature.CONFLIT, e.nature());
        assertEquals("transition-organisation-interdite", e.type());
        Organisation suspendue = active.suspendre(T1);
        assertThrows(TransitionOrganisationInterdite.class, () -> suspendue.suspendre(T1));
    }

    @Test
    void les_statuts_se_lisent_par_leur_code_de_referentiel() {
        for (StatutOrganisation s : StatutOrganisation.values()) {
            assertEquals(s, StatutOrganisation.depuisCode(s.code()));
        }
        assertThrows(IllegalArgumentException.class, () -> StatutOrganisation.depuisCode("fermee"));
    }

    @Test
    void une_organisation_introuvable_est_de_nature_introuvable() {
        OrganisationIntrouvable e = new OrganisationIntrouvable();
        assertEquals(ErreurMetier.Nature.INTROUVABLE, e.nature());
        assertEquals("organisation-introuvable", e.type());
    }
}
