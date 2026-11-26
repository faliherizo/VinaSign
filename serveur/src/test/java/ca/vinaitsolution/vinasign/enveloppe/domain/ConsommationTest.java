package ca.vinaitsolution.vinasign.enveloppe.domain;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ConsommationTest {

    private static final ZoneId MONTREAL = ZoneId.of("America/Toronto");
    private static final UUID A = UUID.randomUUID(), B = UUID.randomUUID(), C = UUID.randomUUID(), D = UUID.randomUUID(), E = UUID.randomUUID();

    private static Consommation.Evenement ev(UUID e, String type, String le) {
        return new Consommation.Evenement(e, type, Instant.parse(le));
    }

    @Test
    void une_enveloppe_compte_une_fois_au_mois_de_son_premier_envoi_dans_le_fuseau_de_la_plateforme() {
        Consommation.Releve r = Consommation.compter(List.of(
                ev(A, "enveloppe-envoyee", "2026-11-01T03:00:00Z"),
                ev(A, "enveloppe-envoyee", "2026-11-05T12:00:00Z"),
                ev(A, "enveloppe-completee", "2026-11-06T12:00:00Z"),
                ev(B, "enveloppe-annulee", "2026-11-02T12:00:00Z"),
                ev(C, "enveloppe-envoyee", "2026-11-03T12:00:00Z"),
                ev(C, "enveloppe-annulee", "2026-11-04T12:00:00Z"),
                ev(D, "enveloppe-envoyee", "2026-12-03T12:00:00Z"),
                ev(D, "signataire-a-refuse", "2026-12-04T12:00:00Z"),
                ev(E, "enveloppe-expiree", "2026-12-05T12:00:00Z"),
                ev(E, "enveloppe-completee", "2026-12-05T13:00:00Z"),
                ev(E, "signataire-a-refuse", "2026-12-05T14:00:00Z"),
                ev(A, "enveloppe-expiree", "2026-12-07T12:00:00Z"),
                ev(A, "champs-definis", "2026-12-08T12:00:00Z")), Set.of(), MONTREAL);
        assertEquals(3, r.envoyees());
        assertEquals(Map.of(YearMonth.of(2026, 10), 1, YearMonth.of(2026, 11), 1, YearMonth.of(2026, 12), 1), r.parMois(),
                "1er novembre 3 h UTC : encore octobre à Montréal");
        assertEquals(1, r.renvois());
        assertEquals(1, r.completees(), "une enveloppe jamais envoyée ne compte pas comme complétée");
        assertEquals(1, r.refusees());
        assertEquals(1, r.annuleesApresEnvoi());
        assertEquals(1, r.annuleesAvantEnvoi());
        assertEquals(1, r.expirees());
    }

    @Test
    void un_envoi_d_une_enveloppe_deja_envoyee_avant_la_periode_est_un_renvoi_non_facture() {
        Consommation.Releve r = Consommation.compter(List.of(ev(A, "enveloppe-envoyee", "2026-11-10T12:00:00Z"),
                ev(A, "enveloppe-annulee", "2026-11-11T12:00:00Z")), Set.of(A), MONTREAL);
        assertEquals(0, r.envoyees());
        assertEquals(1, r.renvois());
        assertEquals(1, r.annuleesApresEnvoi());
        assertTrue(r.parMois().isEmpty());
    }
}
