package ca.vinaitsolution.vinasign.referentiel;

import static ca.vinaitsolution.vinasign.support.Fabrique.type;
import static ca.vinaitsolution.vinasign.support.Fabrique.valeur;
import static org.junit.jupiter.api.Assertions.*;

import ca.vinaitsolution.vinasign.commun.domain.Libelles;
import ca.vinaitsolution.vinasign.referentiel.domain.CodeReferentiel;
import ca.vinaitsolution.vinasign.referentiel.domain.TypeReferentiel;
import ca.vinaitsolution.vinasign.referentiel.domain.ValeurReferentiel;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class ReferentielDomaineTest {

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "Majuscule", "-debut", "avec espace", "é"})
    void un_code_mal_forme_est_refuse_et_cite(String saisie) {
        CodeReferentiel.CodeReferentielInvalide e = assertThrows(CodeReferentiel.CodeReferentielInvalide.class, () -> new CodeReferentiel(saisie));
        assertTrue(e.getMessage().contains(saisie == null ? "rien" : saisie));
    }

    @Test
    void un_code_trop_long_est_refuse_et_tronque_dans_le_message() {
        String long_ = "a".repeat(61);
        CodeReferentiel.CodeReferentielInvalide e = assertThrows(CodeReferentiel.CodeReferentielInvalide.class, () -> new CodeReferentiel(long_));
        assertTrue(e.getMessage().contains("a".repeat(60) + "…"));
        assertEquals("a".repeat(60), new CodeReferentiel("a".repeat(60)).valeur());
    }

    @Test
    void les_valeurs_actives_sont_triees_par_ordre_puis_par_code() {
        TypeReferentiel t = type("essai", valeur("c", 2, true), valeur("b", 1, true), valeur("a", 2, true), valeur("z", 0, false));
        assertEquals(List.of("b", "a", "c"), t.valeursActives().stream().map(v -> v.code().valeur()).toList());
        assertEquals(4, t.valeurs().size());
    }

    @Test
    void une_valeur_inactive_ou_absente_n_est_pas_proposee() {
        TypeReferentiel t = type("essai", valeur("actif", 1, true), valeur("retire", 2, false));
        assertTrue(t.valeurActive(new CodeReferentiel("actif")).isPresent());
        assertTrue(t.valeurActive(new CodeReferentiel("retire")).isEmpty());
        assertTrue(t.valeurActive(new CodeReferentiel("absent")).isEmpty());
    }

    @Test
    void les_collections_sont_copiees_et_immuables() {
        Map<String, String> attributs = new HashMap<>(Map.of("rang", "1"));
        ValeurReferentiel v = new ValeurReferentiel(new CodeReferentiel("a"), Libelles.de(Map.of()), 0, attributs, true);
        attributs.put("rang", "9");
        assertEquals("1", v.attributs().get("rang"));
        assertTrue(new ValeurReferentiel(new CodeReferentiel("a"), Libelles.de(Map.of()), 0, null, true).attributs().isEmpty());
        assertTrue(new TypeReferentiel(new CodeReferentiel("t"), Libelles.de(Map.of()), null).valeurs().isEmpty());
    }

    @Test
    void code_et_libelles_sont_obligatoires() {
        Libelles l = Libelles.de(Map.of());
        CodeReferentiel c = new CodeReferentiel("a");
        assertThrows(IllegalArgumentException.class, () -> new ValeurReferentiel(null, l, 0, Map.of(), true));
        assertThrows(IllegalArgumentException.class, () -> new ValeurReferentiel(c, null, 0, Map.of(), true));
        assertThrows(IllegalArgumentException.class, () -> new TypeReferentiel(null, l, List.of()));
        assertThrows(IllegalArgumentException.class, () -> new TypeReferentiel(c, null, List.of()));
    }
}
