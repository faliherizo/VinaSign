package ca.vinaitsolution.vinasign.commun;

import static org.junit.jupiter.api.Assertions.*;

import ca.vinaitsolution.vinasign.commun.application.LimiterDebit;
import ca.vinaitsolution.vinasign.commun.domain.ContexteAcces;
import ca.vinaitsolution.vinasign.commun.domain.IdentifiantOrganisation;
import ca.vinaitsolution.vinasign.commun.domain.Libelles;
import ca.vinaitsolution.vinasign.referentiel.domain.CodeReferentiel;
import ca.vinaitsolution.vinasign.referentiel.domain.TypeReferentiel;
import ca.vinaitsolution.vinasign.referentiel.domain.ValeurReferentiel;
import ca.vinaitsolution.vinasign.support.ReferentielsEnMemoire;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class LimiterDebitTest {

    private static ValeurReferentiel plan(String code, String debit, boolean actif) {
        return new ValeurReferentiel(new CodeReferentiel(code), Libelles.de(Map.of("fr", code)), 1,
                debit == null ? Map.of() : Map.of("requetesParMinute", debit), actif);
    }

    private final IdentifiantOrganisation petite = new IdentifiantOrganisation(UUID.randomUUID());
    private final IdentifiantOrganisation grande = new IdentifiantOrganisation(UUID.randomUUID());
    private final IdentifiantOrganisation sansDebit = new IdentifiantOrganisation(UUID.randomUUID());
    private final IdentifiantOrganisation planRetire = new IdentifiantOrganisation(UUID.randomUUID());
    private final Map<IdentifiantOrganisation, String> plans = new java.util.HashMap<>(Map.of(petite, "petit", grande, "grand",
            sansDebit, "sans-debit", planRetire, "retire"));
    private final Clock horloge = Clock.fixed(Instant.parse("2026-11-22T18:00:00Z"), ZoneOffset.UTC);
    private final LimiterDebit limiteur = new LimiterDebit(o -> Optional.ofNullable(plans.get(o)), new ReferentielsEnMemoire()
            .avec(new TypeReferentiel(new CodeReferentiel("plan"), Libelles.de(Map.of("fr", "Plan")),
                    List.of(plan("petit", "2", true), plan("grand", "5", true), plan("sans-debit", null, true), plan("retire", "50", false)))),
            3, horloge);

    private int acceptees(String demandeur, IdentifiantOrganisation org, int essais) {
        ContexteAcces c = new ContexteAcces(Optional.ofNullable(org), Optional.empty());
        int n = 0;
        for (int i = 0; i < essais; i++) if (limiteur.passer(new LimiterDebit.Demande(demandeur, c)).acceptee()) n++;
        return n;
    }

    @Test
    void le_debit_vient_du_plan_de_l_organisation() {
        assertEquals(2, acceptees("app-petite", petite, 10));
        assertEquals(5, acceptees("app-grande", grande, 10));
    }

    @Test
    void sans_organisation_plan_inconnu_retire_ou_sans_debit_le_debit_anonyme_s_applique() {
        assertEquals(3, acceptees("adresse:1", null, 10));
        assertEquals(3, acceptees("app-sans", sansDebit, 10));
        assertEquals(3, acceptees("app-retire", planRetire, 10));
        assertEquals(3, acceptees("app-inconnue", new IdentifiantOrganisation(UUID.randomUUID()), 10));
    }

    @Test
    void chaque_demandeur_a_son_seau() {
        assertEquals(2, acceptees("app-1", petite, 5));
        assertEquals(2, acceptees("app-2", petite, 5));
    }

    @Test
    void un_changement_de_plan_s_applique_aussitot_et_le_refus_donne_le_delai() {
        assertEquals(2, acceptees("app", petite, 5));
        plans.put(petite, "grand");
        assertEquals(5, acceptees("app", petite, 10), "nouveau plan, nouveau seau plein");
        LimiterDebit.Reponse r = limiteur.passer(new LimiterDebit.Demande("app", new ContexteAcces(Optional.of(petite), Optional.empty())));
        assertFalse(r.acceptee());
        assertEquals(12, r.attente().toSeconds());
    }

    @Test
    void seuls_les_seaux_pleins_sont_oublies() {
        acceptees("a", petite, 1);
        assertEquals(0, limiteur.oublierInactifs(), "un seau entamé à l'instant n'est pas plein");
        LimiterDebit plusTard = new LimiterDebit(o -> Optional.empty(), new ReferentielsEnMemoire(), 3,
                Clock.offset(horloge, java.time.Duration.ofMinutes(5)));
        assertEquals(0, plusTard.oublierInactifs());
    }
}
