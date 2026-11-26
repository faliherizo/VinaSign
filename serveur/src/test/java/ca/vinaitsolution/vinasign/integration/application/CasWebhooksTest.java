package ca.vinaitsolution.vinasign.integration.application;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import ca.vinaitsolution.vinasign.commun.application.port.Chiffrement;
import ca.vinaitsolution.vinasign.commun.application.port.ContexteAccesCourant;
import ca.vinaitsolution.vinasign.commun.application.port.ExecutionOrganisation;
import ca.vinaitsolution.vinasign.commun.domain.ContexteAcces;
import ca.vinaitsolution.vinasign.integration.application.port.EnvoiWebhook;
import ca.vinaitsolution.vinasign.integration.application.port.GardeDestinations;
import ca.vinaitsolution.vinasign.integration.application.port.Livraisons;
import ca.vinaitsolution.vinasign.integration.domain.ApplicationAttendue;
import ca.vinaitsolution.vinasign.integration.domain.DestinationInvalide;
import ca.vinaitsolution.vinasign.integration.domain.Livraison;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class CasWebhooksTest {

    private static final Instant T = Instant.parse("2026-11-22T12:00:00Z");

    @SuppressWarnings("unchecked")
    private static Livraison tenter(RuntimeException echec) {
        Livraisons livraisons = mock(Livraisons.class);
        Livraison l = Livraison.nouvelle(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), T);
        when(livraisons.dues(any(), anyInt())).thenReturn(List.of(new Livraisons.Due(l, UUID.randomUUID(), "https://rh.exemple.ca/h",
                Base64.getEncoder().encodeToString("chiffre".getBytes()), "envelope.sent", UUID.randomUUID(), T)));
        Chiffrement chiffrement = mock(Chiffrement.class);
        when(chiffrement.dechiffrer(any())).thenReturn("secret");
        EnvoiWebhook envoi = mock(EnvoiWebhook.class);
        GardeDestinations garde = mock(GardeDestinations.class);
        if (echec instanceof DestinationInvalide) {
            doThrow(echec).when(garde).exiger(any());
        } else {
            when(envoi.envoyer(any(), any(), any())).thenThrow(echec);
        }
        ExecutionOrganisation execution = mock(ExecutionOrganisation.class);
        when(execution.pour(any(), any(), any())).thenAnswer(i -> ((Supplier<Object>) i.getArgument(2)).get());
        assertEquals(0, new LivrerWebhooks(livraisons, new LivrerWebhooks.Canal(envoi, garde, chiffrement), execution,
                new LivrerWebhooks.Cadence(Clock.fixed(T, ZoneOffset.UTC), 10)).executer());
        ArgumentCaptor<Livraison> c = ArgumentCaptor.forClass(Livraison.class);
        verify(livraisons).remplacer(c.capture());
        return c.getValue();
    }

    @Test
    void une_destination_injoignable_ou_devenue_interne_est_reprise_plus_tard_sans_code_http() {
        for (RuntimeException echec : List.of(new EnvoiWebhook.Injoignable("coupé", null), new DestinationInvalide("interne"))) {
            Livraison l = tenter(echec);
            assertEquals(1, l.tentatives());
            assertNull(l.dernierCode());
            assertEquals(T.plusSeconds(60), l.prochaineTentative());
        }
    }

    @Test
    void le_corps_ne_porte_que_des_identifiants() {
        UUID evenement = UUID.randomUUID();
        UUID enveloppe = UUID.randomUUID();
        String corps = new String(LivrerWebhooks.corps(new Livraisons.Due(Livraison.nouvelle(UUID.randomUUID(), UUID.randomUUID(), evenement, T),
                UUID.randomUUID(), "u", "s", "envelope.completed", enveloppe, T)), StandardCharsets.UTF_8);
        assertEquals("{\"id\":\"" + evenement + "\",\"type\":\"envelope.completed\",\"createdAt\":\"" + T + "\",\"data\":{\"envelopeId\":\"" + enveloppe + "\"}}",
                corps);
    }

    @Test
    void sans_application_appelante_les_webhooks_sont_refuses() {
        ContexteAccesCourant acces = mock(ContexteAccesCourant.class);
        when(acces.courant()).thenReturn(ContexteAcces.anonyme());
        GererWebhooks g = new GererWebhooks(new GererWebhooks.Registres(null, null, null, null), acces, new GererWebhooks.Securite(null, null, null),
                new GererWebhooks.Horodatage(null, null));
        assertThrows(ApplicationAttendue.class, g::lister);
        assertThrows(ApplicationAttendue.class, () -> g.enregistrer("https://a.ca", null));
    }
}
