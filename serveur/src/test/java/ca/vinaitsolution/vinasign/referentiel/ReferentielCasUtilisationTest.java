package ca.vinaitsolution.vinasign.referentiel;

import static ca.vinaitsolution.vinasign.support.Fabrique.type;
import static ca.vinaitsolution.vinasign.support.Fabrique.valeur;
import static org.junit.jupiter.api.Assertions.*;

import ca.vinaitsolution.vinasign.commun.domain.CodeLangue;
import ca.vinaitsolution.vinasign.referentiel.application.ConsulterReferentiel;
import ca.vinaitsolution.vinasign.referentiel.application.ExigerValeurReferentiel;
import ca.vinaitsolution.vinasign.referentiel.application.ListerReferentiels;
import ca.vinaitsolution.vinasign.referentiel.application.dto.ResumeReferentiel;
import ca.vinaitsolution.vinasign.referentiel.application.dto.VueReferentiel;
import ca.vinaitsolution.vinasign.referentiel.domain.CodeReferentiel;
import ca.vinaitsolution.vinasign.referentiel.domain.ReferentielInconnu;
import ca.vinaitsolution.vinasign.referentiel.domain.ValeurReferentielInconnue;
import ca.vinaitsolution.vinasign.support.ReferentielsEnMemoire;
import java.util.List;
import org.junit.jupiter.api.Test;

class ReferentielCasUtilisationTest {

    private static final CodeLangue FR = new CodeLangue("fr");
    private static final CodeLangue EN = new CodeLangue("en");

    private final ReferentielsEnMemoire depot = new ReferentielsEnMemoire()
            .avec(type("zeta", valeur("z1", 1, true)))
            .avec(type("alpha", valeur("a2", 2, true), valeur("a1", 1, true), valeur("vieux", 3, false)));

    @Test
    void lister_rend_les_types_tries_libelles_et_le_nombre_de_valeurs_actives() {
        List<ResumeReferentiel> r = new ListerReferentiels(depot, FR).executer(EN);
        assertEquals(List.of("alpha", "zeta"), r.stream().map(ResumeReferentiel::code).toList());
        assertEquals("Kind alpha", r.get(0).libelle());
        assertEquals(2, r.get(0).nombreValeurs(), "la valeur retirée ne compte pas");
    }

    @Test
    void consulter_rend_les_valeurs_actives_dans_la_langue_demandee() {
        VueReferentiel v = new ConsulterReferentiel(depot, FR).executer(new CodeReferentiel("alpha"), EN);
        assertEquals("Kind alpha", v.libelle());
        assertEquals("en", v.langue());
        assertEquals(List.of("a1", "a2"), v.valeurs().stream().map(x -> x.code()).toList());
        assertEquals("Label a1", v.valeurs().get(0).libelle());
        assertEquals("1", v.valeurs().get(0).attributs().get("rang"));
        assertEquals("Libellé a1", new ConsulterReferentiel(depot, FR).executer(new CodeReferentiel("alpha"), new CodeLangue("es"))
                .valeurs().get(0).libelle(), "langue absente : repli");
    }

    @Test
    void consulter_un_type_inconnu_est_refuse() {
        ReferentielInconnu e = assertThrows(ReferentielInconnu.class,
                () -> new ConsulterReferentiel(depot, FR).executer(new CodeReferentiel("absent"), FR));
        assertTrue(e.getMessage().contains("absent"));
    }

    @Test
    void exiger_valide_une_saisie_contre_le_referentiel() {
        ExigerValeurReferentiel exiger = new ExigerValeurReferentiel(depot);
        assertEquals("a1", exiger.executer(new CodeReferentiel("alpha"), new CodeReferentiel("a1")).code().valeur());
        ValeurReferentielInconnue retiree = assertThrows(ValeurReferentielInconnue.class,
                () -> exiger.executer(new CodeReferentiel("alpha"), new CodeReferentiel("vieux")));
        assertTrue(retiree.getMessage().contains("vieux") && retiree.getMessage().contains("alpha"));
        assertThrows(ReferentielInconnu.class, () -> exiger.executer(new CodeReferentiel("absent"), new CodeReferentiel("a1")));
    }
}
