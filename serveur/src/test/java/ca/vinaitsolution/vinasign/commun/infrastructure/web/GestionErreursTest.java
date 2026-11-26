package ca.vinaitsolution.vinasign.commun.infrastructure.web;

import static org.junit.jupiter.api.Assertions.*;

import ca.vinaitsolution.vinasign.commun.infrastructure.VinaSignProprietes;
import ca.vinaitsolution.vinasign.referentiel.domain.CodeReferentiel;
import ca.vinaitsolution.vinasign.referentiel.domain.ValeurReferentielInconnue;
import ca.vinaitsolution.vinasign.commun.domain.ErreurMetier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.http.ProblemDetail;

class GestionErreursTest {

    private static final class Essai extends ErreurMetier {
        Essai(Nature nature) {
            super(nature, "essai-" + nature.name().toLowerCase(), "détail " + nature);
        }
    }

    @ParameterizedTest
    @CsvSource({"INTROUVABLE,404", "CONFLIT,409", "INVALIDE,422", "INTERDIT,403", "PERIME,410"})
    void chaque_nature_d_erreur_metier_a_son_statut_et_son_type(ErreurMetier.Nature nature, int statut) {
        ProblemDetail p = new GestionErreurs().metier(new Essai(nature));
        assertEquals(statut, p.getStatus());
        assertEquals(GestionErreurs.BASE + "essai-" + nature.name().toLowerCase(), p.getType().toString());
        assertEquals("détail " + nature, p.getDetail());
    }

    @Test
    void une_valeur_absente_du_referentiel_rend_422_avec_son_type() {
        ProblemDetail p = new GestionErreurs().valeurInconnue(
                new ValeurReferentielInconnue(new CodeReferentiel("type-champ"), new CodeReferentiel("inconnu")));
        assertEquals(422, p.getStatus());
        assertEquals(GestionErreurs.BASE + "valeur-referentiel-inconnue", p.getType().toString());
        assertTrue(p.getDetail().contains("inconnu"));
    }

    @Test
    void sans_origine_configuree_aucune_n_est_autorisee() {
        assertTrue(new VinaSignProprietes("fr", null).originesAutorisees().isEmpty());
    }
}
