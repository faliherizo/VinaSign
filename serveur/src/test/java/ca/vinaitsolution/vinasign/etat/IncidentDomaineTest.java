package ca.vinaitsolution.vinasign.etat;

import static org.junit.jupiter.api.Assertions.*;

import ca.vinaitsolution.vinasign.commun.domain.Libelles;
import ca.vinaitsolution.vinasign.etat.domain.Incident;
import ca.vinaitsolution.vinasign.etat.domain.IncidentClos;
import ca.vinaitsolution.vinasign.etat.domain.IncidentInvalide;
import ca.vinaitsolution.vinasign.etat.domain.MiseAJourIncident;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** VS-124 — un incident s'ouvre par une communication, en reçoit d'autres, et se fige quand une communication le résout. */
class IncidentDomaineTest {

    private static final Instant T = Instant.parse("2026-11-22T12:00:00Z");
    private static final Libelles TITRE = Libelles.de(Map.of("fr", "Lenteurs", "en", "Slowness"));

    private static MiseAJourIncident m(String phase, Instant le) {
        return new MiseAJourIncident(UUID.randomUUID(), phase, Libelles.de(Map.of("fr", "Nous enquêtons.")), le);
    }

    @Test
    void un_incident_se_suit_puis_se_resout_et_se_fige() {
        Incident i = Incident.ouvrir(UUID.randomUUID(), TITRE, "degrade", List.of("api"), m("enquete", T), false);
        assertTrue(i.estOuvert());
        assertEquals(T, i.debut());
        Incident suite = i.publier(m("identifie", T.plusSeconds(60)), false);
        assertEquals(2, suite.misesAJour().size());
        assertEquals(1, i.misesAJour().size(), "immuable");
        Incident resolu = suite.publier(m("resolu", T.plusSeconds(120)), true);
        assertFalse(resolu.estOuvert());
        assertEquals(T.plusSeconds(120), resolu.fin());
        assertThrows(IncidentClos.class, () -> resolu.publier(m("enquete", T.plusSeconds(180)), false));
        assertFalse(Incident.ouvrir(UUID.randomUUID(), TITRE, "majeur", List.of("api"), m("resolu", T), true).estOuvert(), "publié déjà résolu");
    }

    @Test
    void un_incident_incomplet_ou_trop_long_est_refuse() {
        MiseAJourIncident ok = m("enquete", T);
        assertThrows(IncidentInvalide.class, () -> Incident.ouvrir(UUID.randomUUID(), Libelles.de(Map.of()), "degrade", List.of("api"), ok, false));
        assertThrows(IncidentInvalide.class, () -> Incident.ouvrir(UUID.randomUUID(), null, "degrade", List.of("api"), ok, false));
        assertThrows(IncidentInvalide.class, () -> Incident.ouvrir(UUID.randomUUID(), Libelles.de(Map.of("fr", "x".repeat(201))), "degrade", List.of("api"), ok, false));
        assertTrue(assertThrows(IncidentInvalide.class, () -> Incident.ouvrir(UUID.randomUUID(), TITRE, "degrade", List.of(), ok, false))
                .getMessage().contains("composant"));
        assertThrows(IncidentInvalide.class, () -> Incident.ouvrir(UUID.randomUUID(), TITRE, "degrade", null, ok, false));
        assertThrows(IllegalArgumentException.class, () -> new Incident(null, TITRE, "degrade", List.of("api"), T, null, null));
        assertThrows(IllegalArgumentException.class, () -> new Incident(UUID.randomUUID(), TITRE, null, List.of("api"), T, null, null));
        assertThrows(IllegalArgumentException.class, () -> new Incident(UUID.randomUUID(), TITRE, " ", List.of("api"), T, null, null));
        assertThrows(IllegalArgumentException.class, () -> new Incident(UUID.randomUUID(), TITRE, "degrade", List.of("api"), null, null, null));
        assertThrows(IllegalArgumentException.class, () -> new Incident(UUID.randomUUID(), TITRE, "degrade", List.of("api"), T, T.minusSeconds(1), null));
        assertTrue(new Incident(UUID.randomUUID(), TITRE, "degrade", List.of("api"), T, T, null).misesAJour().isEmpty());
        assertThrows(IllegalArgumentException.class, () -> new MiseAJourIncident(null, "enquete", TITRE, T));
        assertThrows(IllegalArgumentException.class, () -> new MiseAJourIncident(UUID.randomUUID(), null, TITRE, T));
        assertThrows(IllegalArgumentException.class, () -> new MiseAJourIncident(UUID.randomUUID(), "", TITRE, T));
        assertThrows(IllegalArgumentException.class, () -> new MiseAJourIncident(UUID.randomUUID(), "enquete", TITRE, null));
        assertThrows(IncidentInvalide.class, () -> new MiseAJourIncident(UUID.randomUUID(), "enquete", Libelles.de(Map.of("fr", "x".repeat(2001))), T));
    }
}
