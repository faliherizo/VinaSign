package ca.vinaitsolution.vinasign.referentiel.infrastructure.web;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Optional;
import org.junit.jupiter.api.Test;

class PreferenceLangueTest {

    @Test
    void la_liste_ponderee_d_un_navigateur_donne_sa_langue_de_plus_fort_poids() {
        assertEquals(Optional.of("fr"), PreferenceLangue.depuis("fr-CA,fr;q=0.9,en-US;q=0.8,en;q=0.7"));
        assertEquals(Optional.of("en"), PreferenceLangue.depuis("fr;q=0.5,en-GB;q=0.9"));
    }

    @Test
    void a_poids_egal_la_premiere_langue_de_l_en_tete_l_emporte() {
        assertEquals(Optional.of("en"), PreferenceLangue.depuis("en,fr"));
    }

    @Test
    void une_balise_unique_reste_acceptee() {
        assertEquals(Optional.of("en"), PreferenceLangue.depuis("en-CA"));
    }

    @Test
    void joker_poids_nul_absence_ou_en_tete_mal_forme_ne_donnent_aucune_preference() {
        assertEquals(Optional.empty(), PreferenceLangue.depuis(null));
        assertEquals(Optional.empty(), PreferenceLangue.depuis("  "));
        assertEquals(Optional.empty(), PreferenceLangue.depuis("*"));
        assertEquals(Optional.empty(), PreferenceLangue.depuis("fr;q=0"));
        assertEquals(Optional.empty(), PreferenceLangue.depuis("fr;q=x,,"));
    }
}
