package ca.vinaitsolution.vinasign.enveloppe.application;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import ca.vinaitsolution.vinasign.commun.application.port.ActeurCourant;
import ca.vinaitsolution.vinasign.commun.application.port.ArchivageLongTerme;
import ca.vinaitsolution.vinasign.commun.application.port.ExecutionOrganisation;
import ca.vinaitsolution.vinasign.commun.application.port.GenerateurIdentifiant;
import ca.vinaitsolution.vinasign.commun.domain.Condensats;
import ca.vinaitsolution.vinasign.commun.domain.IdentifiantOrganisation;
import ca.vinaitsolution.vinasign.enveloppe.application.port.AlertesArchive;
import ca.vinaitsolution.vinasign.enveloppe.application.port.ArchivesDocuments;
import ca.vinaitsolution.vinasign.enveloppe.application.port.Enveloppes;
import ca.vinaitsolution.vinasign.enveloppe.application.port.JournalEnveloppes;
import ca.vinaitsolution.vinasign.enveloppe.application.port.StockageDocuments;
import ca.vinaitsolution.vinasign.enveloppe.application.port.VersionsDocuments;
import ca.vinaitsolution.vinasign.enveloppe.domain.EmpreinteAlteree;
import ca.vinaitsolution.vinasign.enveloppe.domain.Enveloppe;
import ca.vinaitsolution.vinasign.enveloppe.domain.EtatArchive;
import ca.vinaitsolution.vinasign.enveloppe.domain.EvenementEnveloppe;
import ca.vinaitsolution.vinasign.enveloppe.domain.IdentifiantEnveloppe;
import ca.vinaitsolution.vinasign.enveloppe.domain.ModeEnveloppe;
import ca.vinaitsolution.vinasign.enveloppe.domain.VersionDocument;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * VS-132 — un état archivé n'est renouvelé qu'à l'approche de son échéance ; un final jamais évalué l'est d'abord ; un
 * renouvellement est conservé sous une clé propre et consigné ; un échec est signalé sans arrêter le lot.
 */
class RenouvelerArchivesTest {

    private static final Instant T = Instant.parse("2026-11-22T12:00:00Z");
    private static final Duration MARGE = Duration.ofDays(90);
    private static final byte[] FINAL = "pdf final".getBytes();
    private static final byte[] RENOUVELE = "pdf renouvele".getBytes();

    private final ArchivesDocuments archives = mock(ArchivesDocuments.class);
    private final VersionsDocuments versions = mock(VersionsDocuments.class);
    private final StockageDocuments stockage = mock(StockageDocuments.class);
    private final ArchivageLongTerme archivage = mock(ArchivageLongTerme.class);
    private final Enveloppes enveloppes = mock(Enveloppes.class);
    private final JournalEnveloppes journal = mock(JournalEnveloppes.class);
    private final AlertesArchive alertes = mock(AlertesArchive.class);
    private final ExecutionOrganisation execution = new ExecutionOrganisation() {
        @Override
        public <T> T pour(IdentifiantOrganisation organisation, String acteur, Supplier<T> action) {
            return action.get();
        }
    };
    private final IdentifiantEnveloppe id = new IdentifiantEnveloppe(UUID.randomUUID());
    private final IdentifiantOrganisation org = new IdentifiantOrganisation(UUID.randomUUID());
    private RenouvelerArchives renouvellement;

    @BeforeEach
    void preparer() {
        ActeurCourant acteur = mock(ActeurCourant.class, RETURNS_DEEP_STUBS);
        when(acteur.courant().reference()).thenReturn(RenouvelerArchives.ACTEUR);
        GenerateurIdentifiant identifiants = UUID::randomUUID;
        ServicesEnveloppe services = new ServicesEnveloppe(enveloppes, journal, null, acteur, identifiants, Clock.fixed(T, ZoneOffset.UTC), e -> { });
        renouvellement = new RenouvelerArchives(new RenouvelerArchives.Documents(archives, versions, stockage, archivage), services,
                new RenouvelerArchives.Exploitation(execution, alertes, MARGE, 10));
        when(archives.aRenouveler(T.plus(MARGE), 10)).thenReturn(List.of(new ArchivesDocuments.ARenouveler(id.valeur(), org.valeur())));
        when(enveloppes.parId(id)).thenReturn(Optional.of(Enveloppe.creer(id, org, "app", "R-1", ModeEnveloppe.DOCUMENT, T.plusSeconds(60), T)));
    }

    private EtatArchive etat(byte[] contenu, Instant echeance) {
        return new EtatArchive(UUID.randomUUID(), id, "cle-courante", Condensats.sha256Hex(contenu), contenu.length, echeance, T.minusSeconds(3600));
    }

    @Test
    void un_etat_proche_de_l_echeance_est_renouvele_conserve_et_consigne() {
        when(archives.dernier(id)).thenReturn(Optional.of(etat(FINAL, T.plus(Duration.ofDays(30)))));
        when(stockage.lire("cle-courante")).thenReturn(FINAL);
        when(archivage.renouveler(FINAL)).thenReturn(RENOUVELE);
        when(archivage.echeance(RENOUVELE)).thenReturn(Optional.of(T.plus(Duration.ofDays(3650))));
        assertEquals(1, renouvellement.executer());
        ArgumentCaptor<String> cle = ArgumentCaptor.forClass(String.class);
        verify(stockage).ecrire(cle.capture(), eq(RENOUVELE));
        assertTrue(cle.getValue().startsWith("enveloppes/" + org.valeur() + "/" + id.valeur() + "/archive-"), "clé propre, jamais réécrite");
        ArgumentCaptor<EtatArchive> ajoute = ArgumentCaptor.forClass(EtatArchive.class);
        verify(archives).ajouter(ajoute.capture());
        assertEquals(cle.getValue(), ajoute.getValue().cleObjet());
        assertEquals(Condensats.sha256Hex(RENOUVELE), ajoute.getValue().sha256());
        assertEquals(T.plus(Duration.ofDays(3650)), ajoute.getValue().echeance());
        ArgumentCaptor<EvenementEnveloppe> evenement = ArgumentCaptor.forClass(EvenementEnveloppe.class);
        verify(journal).consigner(evenement.capture());
        assertEquals(RenouvelerArchives.EVENEMENT, evenement.getValue().type());
        assertEquals(Condensats.sha256Hex(RENOUVELE), evenement.getValue().donnees().get("sha256"));
        verifyNoInteractions(alertes);
    }

    @Test
    void un_final_jamais_evalue_est_note_puis_laisse_tel_quel_s_il_n_echoit_pas() {
        when(archives.dernier(id)).thenReturn(Optional.empty());
        VersionDocument finale = new VersionDocument(UUID.randomUUID(), id, "final", "cle-finale", Condensats.sha256Hex(FINAL), FINAL.length, 1, T);
        when(versions.de(id)).thenReturn(List.of(new VersionDocument(UUID.randomUUID(), id, "original", "o", "0".repeat(64), 1, 1, T), finale));
        when(stockage.lire("cle-finale")).thenReturn(FINAL);
        when(archivage.echeance(FINAL)).thenReturn(Optional.of(T.plus(Duration.ofDays(365))));
        assertEquals(0, renouvellement.executer());
        ArgumentCaptor<EtatArchive> premier = ArgumentCaptor.forClass(EtatArchive.class);
        verify(archives).ajouter(premier.capture());
        assertEquals("cle-finale", premier.getValue().cleObjet());
        assertEquals(T.plus(Duration.ofDays(365)), premier.getValue().echeance());
        verify(archivage, never()).renouveler(any());
        when(archivage.echeance(FINAL)).thenReturn(Optional.empty());
        assertEquals(0, renouvellement.executer(), "sans horodatage : noté sans échéance, jamais renouvelé");
        verify(archivage, never()).renouveler(any());
    }

    @Test
    void un_echec_est_signale_sans_arreter_le_lot() {
        IdentifiantEnveloppe autre = new IdentifiantEnveloppe(UUID.randomUUID());
        when(archives.aRenouveler(T.plus(MARGE), 10)).thenReturn(List.of(new ArchivesDocuments.ARenouveler(id.valeur(), org.valeur()),
                new ArchivesDocuments.ARenouveler(autre.valeur(), org.valeur())));
        when(archives.dernier(id)).thenReturn(Optional.of(etat(FINAL, T)));
        when(stockage.lire("cle-courante")).thenReturn("altéré".getBytes());
        when(archives.dernier(autre)).thenReturn(Optional.empty());
        when(versions.de(autre)).thenReturn(List.of());
        assertEquals(0, renouvellement.executer());
        verify(alertes).echec(eq(id), any(EmpreinteAlteree.class));
        verify(alertes).echec(eq(autre), any(IllegalStateException.class));
        verify(stockage, never()).ecrire(any(), any());
    }

    @Test
    void un_renouvellement_sans_horodatage_ou_sans_enveloppe_echoue() {
        when(archives.dernier(id)).thenReturn(Optional.of(etat(FINAL, T)));
        when(stockage.lire("cle-courante")).thenReturn(FINAL);
        when(archivage.renouveler(FINAL)).thenReturn(RENOUVELE);
        when(archivage.echeance(RENOUVELE)).thenReturn(Optional.empty());
        assertEquals(0, renouvellement.executer());
        verify(alertes).echec(eq(id), argThat(e -> e.getMessage().contains("sans horodatage")));
        when(enveloppes.parId(id)).thenReturn(Optional.empty());
        assertEquals(0, renouvellement.executer());
        verify(alertes).echec(eq(id), argThat(e -> e.getMessage().contains("introuvable")));
        verify(archives, never()).ajouter(any());
    }
}
