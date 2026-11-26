package ca.vinaitsolution.vinasign.notification;

import static ca.vinaitsolution.vinasign.support.Fabrique.type;
import static org.junit.jupiter.api.Assertions.*;

import ca.vinaitsolution.vinasign.commun.domain.CodeLangue;
import ca.vinaitsolution.vinasign.commun.domain.Libelles;
import ca.vinaitsolution.vinasign.notification.application.ExpedierCourriels;
import ca.vinaitsolution.vinasign.notification.application.ModelesCourriel;
import ca.vinaitsolution.vinasign.notification.application.port.BoiteEnvoi;
import ca.vinaitsolution.vinasign.notification.domain.CourrielSortant;
import ca.vinaitsolution.vinasign.referentiel.domain.CodeReferentiel;
import ca.vinaitsolution.vinasign.referentiel.domain.ValeurReferentiel;
import ca.vinaitsolution.vinasign.support.ReferentielsEnMemoire;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class NotificationUnitaireTest {

    private static final Instant T0 = Instant.parse("2026-11-22T22:00:00Z");

    private static CourrielSortant courriel(int tentatives) {
        return new CourrielSortant(UUID.randomUUID(), UUID.randomUUID(), "u@exemple.ca", "Sujet", "Corps {lien}", tentatives, T0);
    }

    @Test
    void la_reprise_attend_de_plus_en_plus_longtemps_jusqu_a_une_heure() {
        assertEquals(T0.plusSeconds(60), courriel(0).prochaineApresEchec(T0));
        assertEquals(T0.plusSeconds(4 * 60), courriel(2).prochaineApresEchec(T0));
        assertEquals(T0.plusSeconds(3600), courriel(6).prochaineApresEchec(T0));
        assertEquals(T0.plusSeconds(3600), courriel(30).prochaineApresEchec(T0));
    }

    @Test
    void un_courriel_ne_livre_ni_destinataire_ni_corps_dans_une_trace_et_doit_etre_complet() {
        assertFalse(courriel(0).toString().contains("u@exemple.ca"));
        assertFalse(courriel(0).toString().contains("Corps"));
        UUID id = UUID.randomUUID();
        assertThrows(IllegalArgumentException.class, () -> new CourrielSortant(null, id, "d", "s", "c", 0, T0));
        assertThrows(IllegalArgumentException.class, () -> new CourrielSortant(id, null, "d", "s", "c", 0, T0));
        assertThrows(IllegalArgumentException.class, () -> new CourrielSortant(id, id, null, "s", "c", 0, T0));
        assertThrows(IllegalArgumentException.class, () -> new CourrielSortant(id, id, " ", "s", "c", 0, T0));
        assertThrows(IllegalArgumentException.class, () -> new CourrielSortant(id, id, "d", null, "c", 0, T0));
        assertThrows(IllegalArgumentException.class, () -> new CourrielSortant(id, id, "d", "s", null, 0, T0));
        assertThrows(IllegalArgumentException.class, () -> new CourrielSortant(id, id, "d", "s", "c", 0, null));
        assertThrows(IllegalArgumentException.class, () -> new CourrielSortant(id, id, "d", "s", "c", -1, T0));
    }

    /** Boîte d'envoi en mémoire, qui consigne les décisions de l'expédition. */
    private static final class Boite implements BoiteEnvoi {
        final List<CourrielSortant> dus = new ArrayList<>();
        final List<String> decisions = new ArrayList<>();

        public void deposer(CourrielSortant c) {
            dus.add(c);
        }

        public List<CourrielSortant> dus(Instant maintenant, int limite) {
            return dus.stream().limit(limite).toList();
        }

        public void envoye(UUID id, Instant le) {
            decisions.add("envoye");
        }

        public void echec(UUID id, int tentatives, Instant prochaine, boolean abandonne) {
            decisions.add("echec:" + tentatives + ":" + prochaine + ":" + abandonne);
        }
    }

    @Test
    void un_echec_est_repris_puis_abandonne_au_plafond_et_un_succes_compte() {
        Boite boite = new Boite();
        boite.deposer(courriel(0));
        boite.deposer(courriel(2));
        boite.deposer(courriel(0));
        List<String> envois = new ArrayList<>();
        ExpedierCourriels expedition = new ExpedierCourriels(boite, (d, s, c) -> {
            if (envois.size() < 2) {
                envois.add(d);
                if (envois.size() == 1) throw new IllegalStateException("relais indisponible");
            }
        }, Clock.fixed(T0, ZoneOffset.UTC), 10, 3);
        assertEquals(2, expedition.executer());
        assertEquals(List.of("echec:1:" + T0.plusSeconds(60) + ":false", "envoye", "envoye"), boite.decisions);
        Boite derniere = new Boite();
        derniere.deposer(courriel(2));
        new ExpedierCourriels(derniere, (d, s, c) -> { throw new IllegalStateException(); }, Clock.fixed(T0, ZoneOffset.UTC), 10, 3).executer();
        assertTrue(derniere.decisions.get(0).endsWith(":true"), "troisième échec : abandon");
    }

    @Test
    void un_modele_se_rend_dans_la_langue_demandee_ou_la_langue_de_repli() {
        ReferentielsEnMemoire r = new ReferentielsEnMemoire().avec(type("modele-courriel",
                new ValeurReferentiel(new CodeReferentiel("essai-sujet"), Libelles.de(Map.of("fr", "Pour {nom}", "en", "For {nom}")), 1, Map.of(), true),
                new ValeurReferentiel(new CodeReferentiel("essai-corps"), Libelles.de(Map.of("fr", "Lien : {lien}")), 2, Map.of(), true)));
        ModelesCourriel m = new ModelesCourriel(r, new CodeLangue("fr"));
        ModelesCourriel.Rendu en = m.rendre("essai", "en", Map.of("nom", "Ann", "lien", "https://x/s/abc"));
        assertEquals("For Ann", en.sujet());
        assertEquals("Lien : https://x/s/abc", en.corps(), "langue absente : repli");
        assertThrows(IllegalStateException.class, () -> m.rendre("absent", "fr", Map.of()));
    }
}
