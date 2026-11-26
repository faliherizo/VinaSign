package ca.vinaitsolution.vinasign.notification.application;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import ca.vinaitsolution.vinasign.commun.application.dto.DossierEnveloppe;
import ca.vinaitsolution.vinasign.commun.application.port.GenerateurIdentifiant;
import ca.vinaitsolution.vinasign.commun.application.port.LectureEnveloppes;
import ca.vinaitsolution.vinasign.commun.domain.EvenementMetier;
import ca.vinaitsolution.vinasign.commun.domain.IdentifiantOrganisation;
import ca.vinaitsolution.vinasign.notification.application.port.BoiteEnvoi;
import ca.vinaitsolution.vinasign.notification.domain.CourrielSortant;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ConfirmerParCourrielTest {

    @Test
    void seuls_les_signataires_joignables_par_courriel_recoivent_la_confirmation_dans_leur_langue() {
        UUID id = UUID.randomUUID();
        UUID org = UUID.randomUUID();
        DossierEnveloppe d = new DossierEnveloppe(id, org, "app", "REF-1", "document", "terminee", Instant.now(), List.of(
                new DossierEnveloppe.SignataireDossier(UUID.randomUUID(), "Alice", "alice@exemple.ca", "en", "invite", 1, "signe", null, false, null),
                new DossierEnveloppe.SignataireDossier(UUID.randomUUID(), "Dre Roy", null, "fr", "utilisateur-application", 2, "signe", "m-1", false, null)));
        LectureEnveloppes enveloppes = mock(LectureEnveloppes.class);
        when(enveloppes.dossier(id)).thenReturn(Optional.of(d));
        ModelesCourriel modeles = mock(ModelesCourriel.class);
        when(modeles.rendre(anyString(), anyString(), anyMap())).thenReturn(new ModelesCourriel.Rendu("Signing complete", "Hello Alice"));
        BoiteEnvoi boite = mock(BoiteEnvoi.class);
        GenerateurIdentifiant ids = UUID::randomUUID;
        ConfirmerParCourriel c = new ConfirmerParCourriel(boite, enveloppes, modeles, ids, Clock.systemUTC());
        assertTrue(c.concerne("enveloppe-completee"));
        assertFalse(c.concerne("enveloppe-envoyee"));
        c.recevoir(new EvenementMetier("enveloppe-completee", new IdentifiantOrganisation(org), Map.of("enveloppe", id.toString())));
        ArgumentCaptor<CourrielSortant> envoye = ArgumentCaptor.forClass(CourrielSortant.class);
        verify(boite).deposer(envoye.capture());
        assertEquals("alice@exemple.ca", envoye.getValue().destinataire());
        verify(modeles).rendre("confirmation", "en", Map.of("nom", "Alice", "reference", "REF-1"));
        when(enveloppes.dossier(any())).thenReturn(Optional.empty());
        assertThrows(IllegalStateException.class, () -> c.recevoir(new EvenementMetier("enveloppe-completee", new IdentifiantOrganisation(org),
                Map.of("enveloppe", UUID.randomUUID().toString()))));
    }
}
