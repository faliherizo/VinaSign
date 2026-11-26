package ca.vinaitsolution.vinasign.enveloppe.application;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import ca.vinaitsolution.vinasign.enveloppe.application.port.JournalPreuve;
import ca.vinaitsolution.vinasign.enveloppe.domain.Ancrage;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AncrerJournalTest {

    private static final Instant T = Instant.parse("2026-11-22T12:00:00Z");

    @Test
    void une_fenetre_deja_ancree_ou_sans_evenement_ne_produit_aucun_ancrage() {
        JournalPreuve journal = mock(JournalPreuve.class);
        AncrerJournal a = new AncrerJournal(journal, null, null, Clock.fixed(T, ZoneOffset.UTC), Duration.ofMinutes(1));
        when(journal.dernierAncrage()).thenReturn(Optional.of(new Ancrage(UUID.randomUUID(), T.minusSeconds(3600), T.minusSeconds(30), 3, "e", new byte[1], T)));
        assertTrue(a.executer().isEmpty(), "le dernier ancrage couvre déjà la fenêtre (marge d'une minute)");
        verify(journal, never()).agreger(any(), any());
        when(journal.dernierAncrage()).thenReturn(Optional.empty());
        when(journal.agreger(Instant.EPOCH, T.minusSeconds(60))).thenReturn(new JournalPreuve.Agregat(0, "e"));
        assertTrue(a.executer().isEmpty(), "rien de consigné");
        verify(journal, never()).ajouter(any());
    }
}
