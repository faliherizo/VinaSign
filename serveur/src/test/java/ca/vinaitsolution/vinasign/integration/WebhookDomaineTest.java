package ca.vinaitsolution.vinasign.integration;

import static org.junit.jupiter.api.Assertions.*;

import ca.vinaitsolution.vinasign.integration.domain.DestinationInvalide;
import ca.vinaitsolution.vinasign.integration.domain.DestinationWebhook;
import ca.vinaitsolution.vinasign.integration.domain.Livraison;
import ca.vinaitsolution.vinasign.integration.application.SignatureWebhook;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class WebhookDomaineTest {

    private static final byte[] SECRET = "secret-de-test".getBytes(StandardCharsets.UTF_8);
    private static final byte[] CORPS = "{\"id\":\"1\"}".getBytes(StandardCharsets.UTF_8);
    private static final Instant T = Instant.parse("2026-11-22T12:00:00Z");

    @Test
    void la_signature_est_verifiable_horodatee_et_liee_au_corps_et_au_secret() {
        String entete = SignatureWebhook.signer(SECRET, 1_000, CORPS);
        assertTrue(entete.matches("t=1000,v1=[0-9a-f]{64}"));
        assertTrue(SignatureWebhook.verifier(SECRET, entete, CORPS, 1_200, 300));
        assertFalse(SignatureWebhook.verifier(SECRET, entete, CORPS, 1_400, 300), "trop ancienne : rejeu refusé");
        assertFalse(SignatureWebhook.verifier(SECRET, entete, CORPS, 600, 300), "datée du futur");
        assertFalse(SignatureWebhook.verifier("autre".getBytes(), entete, CORPS, 1_000, 300));
        assertFalse(SignatureWebhook.verifier(SECRET, entete, "{}".getBytes(), 1_000, 300));
        assertFalse(SignatureWebhook.verifier(SECRET, entete.replace("t=1000", "t=1001"), CORPS, 1_000, 300), "horodatage signé");
        assertFalse(SignatureWebhook.verifier(SECRET, null, CORPS, 1_000, 300));
        assertFalse(SignatureWebhook.verifier(SECRET, "v1=abc", CORPS, 1_000, 300));
    }

    @Test
    void reprise_a_delai_croissant_puis_abandon_au_dela_de_72_heures() {
        Livraison l = Livraison.nouvelle(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), T);
        assertFalse(l.terminee());
        Instant le = T;
        Duration[] attendus = {Duration.ofMinutes(1), Duration.ofMinutes(5), Duration.ofMinutes(15), Duration.ofHours(1), Duration.ofHours(4)};
        for (Duration d : attendus) {
            l = l.echouee(500, le);
            assertEquals(le.plus(d), l.prochaineTentative());
            assertEquals(500, l.dernierCode());
            le = l.prochaineTentative();
        }
        while (!l.terminee()) {
            l = l.echouee(null, l.prochaineTentative());
        }
        assertNotNull(l.abandonneLe());
        assertTrue(l.abandonneLe().isBefore(T.plus(Livraison.FENETRE)));
        assertNull(l.dernierCode(), "injoignable");
        Livraison finale = l;
        assertThrows(IllegalStateException.class, () -> finale.echouee(500, T));
        assertThrows(IllegalStateException.class, () -> finale.reussie(200, T));
    }

    @Test
    void une_livraison_reussie_est_terminee_et_complete() {
        Livraison l = Livraison.nouvelle(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), T).reussie(204, T.plusSeconds(1));
        assertTrue(l.terminee());
        assertEquals(1, l.tentatives());
        assertEquals(204, l.dernierCode());
        assertThrows(IllegalArgumentException.class, () -> new Livraison(null, UUID.randomUUID(), UUID.randomUUID(), T, 0, T, null, null, null));
        assertThrows(IllegalArgumentException.class, () -> new Livraison(UUID.randomUUID(), null, UUID.randomUUID(), T, 0, T, null, null, null));
        assertThrows(IllegalArgumentException.class, () -> new Livraison(UUID.randomUUID(), UUID.randomUUID(), null, T, 0, T, null, null, null));
        assertThrows(IllegalArgumentException.class, () -> new Livraison(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), null, 0, T, null, null, null));
        assertThrows(IllegalArgumentException.class, () -> new Livraison(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), T, 0, null, null, null, null));
        assertThrows(IllegalArgumentException.class, () -> new Livraison(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), T, -1, T, null, null, null));
        assertTrue(new Livraison(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), T, 1, T, null, T, null).terminee());
    }

    @Test
    void une_destination_a_une_url_bornee_et_des_evenements_figes() {
        UUID id = UUID.randomUUID();
        UUID org = UUID.randomUUID();
        assertEquals(Set.of(), new DestinationWebhook(id, org, "app", "https://a.ca/h", null, T).evenements());
        assertThrows(UnsupportedOperationException.class, () -> new DestinationWebhook(id, org, "app", "https://a.ca/h",
                new java.util.HashSet<>(Set.of("envelope.sent")), T).evenements().add("x"));
        assertThrows(DestinationInvalide.class, () -> new DestinationWebhook(id, org, "app", " ", Set.of(), T));
        assertThrows(DestinationInvalide.class, () -> new DestinationWebhook(id, org, "app", null, Set.of(), T));
        assertThrows(DestinationInvalide.class, () -> new DestinationWebhook(id, org, "app", "https://a.ca/" + "x".repeat(2000), Set.of(), T));
        assertThrows(IllegalArgumentException.class, () -> new DestinationWebhook(null, org, "app", "https://a.ca", Set.of(), T));
        assertThrows(IllegalArgumentException.class, () -> new DestinationWebhook(id, null, "app", "https://a.ca", Set.of(), T));
        assertThrows(IllegalArgumentException.class, () -> new DestinationWebhook(id, org, null, "https://a.ca", Set.of(), T));
        assertThrows(IllegalArgumentException.class, () -> new DestinationWebhook(id, org, "app", "https://a.ca", Set.of(), null));
    }
}
