package ca.vinaitsolution.vinasign.etat;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import ca.vinaitsolution.vinasign.commun.domain.Libelles;
import ca.vinaitsolution.vinasign.etat.application.ConsulterEtat;
import ca.vinaitsolution.vinasign.etat.application.port.Incidents;
import ca.vinaitsolution.vinasign.etat.domain.Incident;
import ca.vinaitsolution.vinasign.etat.domain.MiseAJourIncident;
import ca.vinaitsolution.vinasign.referentiel.application.port.Referentiels;
import ca.vinaitsolution.vinasign.referentiel.domain.CodeReferentiel;
import ca.vinaitsolution.vinasign.referentiel.domain.TypeReferentiel;
import ca.vinaitsolution.vinasign.support.Fabrique;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** VS-124 — gravité tirée de l'ordre du référentiel ; un impact retiré du référentiel compte comme l'état normal. */
class ConsulterEtatTest {

    private static final Instant T = Instant.parse("2026-11-22T12:00:00Z");

    private final Incidents incidents = mock(Incidents.class);
    private final Referentiels referentiels = mock(Referentiels.class);
    private final ConsulterEtat etat = new ConsulterEtat(incidents, referentiels, Clock.fixed(T, ZoneOffset.UTC), Duration.ofDays(90));

    private static Incident ouvert(String impact, String composant) {
        return Incident.ouvrir(UUID.randomUUID(), Libelles.de(Map.of("fr", "x")), impact, List.of(composant),
                new MiseAJourIncident(UUID.randomUUID(), "enquete", Libelles.de(Map.of("fr", "x")), T), false);
    }

    @Test
    void le_plus_grave_des_incidents_ouverts_l_emporte() {
        when(referentiels.type(new CodeReferentiel("impact-incident"))).thenReturn(Optional.of(Fabrique.type("impact-incident",
                Fabrique.valeur("normal", 1, true), Fabrique.valeur("leger", 2, true), Fabrique.valeur("grave", 3, true))));
        when(referentiels.type(new CodeReferentiel("composant-service"))).thenReturn(Optional.of(Fabrique.type("composant-service",
                Fabrique.valeur("api", 1, true), Fabrique.valeur("portail", 2, true))));
        when(incidents.depuis(T.minus(Duration.ofDays(90)))).thenReturn(List.of(ouvert("leger", "api"), ouvert("grave", "api"), ouvert("retire", "portail")));
        var vue = etat.consulter();
        assertEquals("grave", vue.impact());
        assertEquals(Map.of("api", "grave", "portail", "retire"), vue.composants(), "impact retiré : gravité minimale, mais affiché tel quel");
        assertEquals(3, vue.incidents().size());
    }

    @Test
    void sans_referentiel_des_impacts_l_etat_ne_se_calcule_pas() {
        when(referentiels.type(any())).thenReturn(Optional.empty());
        assertThrows(IllegalStateException.class, etat::consulter);
        when(referentiels.type(new CodeReferentiel("impact-incident"))).thenReturn(Optional.of(new TypeReferentiel(new CodeReferentiel("impact-incident"),
                Libelles.de(Map.of()), List.of())));
        assertThrows(IllegalStateException.class, etat::consulter);
    }
}
