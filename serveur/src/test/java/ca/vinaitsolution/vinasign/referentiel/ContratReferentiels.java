package ca.vinaitsolution.vinasign.referentiel;

import static ca.vinaitsolution.vinasign.support.Fabrique.codeUnique;
import static ca.vinaitsolution.vinasign.support.Fabrique.type;
import static ca.vinaitsolution.vinasign.support.Fabrique.valeur;
import static org.junit.jupiter.api.Assertions.*;

import ca.vinaitsolution.vinasign.commun.domain.CodeLangue;
import ca.vinaitsolution.vinasign.referentiel.application.port.Referentiels;
import ca.vinaitsolution.vinasign.referentiel.domain.CodeReferentiel;
import ca.vinaitsolution.vinasign.referentiel.domain.TypeReferentiel;
import org.junit.jupiter.api.Test;

/**
 * Contrat observable du port {@link Referentiels}, exécuté contre chaque implémentation (substitution de Liskov) : une
 * implémentation qui le passe est interchangeable avec les autres.
 */
abstract class ContratReferentiels {

    /** Implémentation éprouvée. */
    protected abstract Referentiels referentiels();

    /** Enregistre un type dans le support de l'implémentation, comme le ferait l'administration. */
    protected abstract void enregistrer(TypeReferentiel type);

    @Test
    void un_type_enregistre_est_relu_a_l_identique() {
        String code = codeUnique("contrat");
        enregistrer(type(code, valeur("b", 2, true), valeur("a", 1, true), valeur("retire", 3, false)));
        TypeReferentiel relu = referentiels().type(new CodeReferentiel(code)).orElseThrow();
        assertEquals(code, relu.code().valeur());
        assertEquals("Kind " + code, relu.libelles().pour(new CodeLangue("en"), new CodeLangue("fr"), code));
        assertEquals(3, relu.valeurs().size(), "les valeurs inactives sont relues aussi");
        assertEquals("a", relu.valeursActives().get(0).code().valeur());
        assertEquals("1", relu.valeursActives().get(0).attributs().get("rang"));
        assertEquals("Libellé a", relu.valeursActives().get(0).libelles().pour(new CodeLangue("fr"), new CodeLangue("fr"), "a"));
    }

    @Test
    void un_type_enregistre_figure_dans_la_liste() {
        String code = codeUnique("liste");
        enregistrer(type(code, valeur("x", 1, true)));
        assertTrue(referentiels().types().stream().anyMatch(t -> t.code().valeur().equals(code)));
    }

    @Test
    void un_type_absent_rend_vide() {
        assertTrue(referentiels().type(new CodeReferentiel(codeUnique("absent"))).isEmpty());
    }
}
