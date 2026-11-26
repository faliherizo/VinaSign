package ca.vinaitsolution.vinasign.enveloppe;

import static org.junit.jupiter.api.Assertions.*;

import ca.vinaitsolution.vinasign.commun.domain.ErreurMetier;
import ca.vinaitsolution.vinasign.commun.domain.IdentifiantOrganisation;
import ca.vinaitsolution.vinasign.enveloppe.domain.ApplicationRequise;
import ca.vinaitsolution.vinasign.enveloppe.domain.DepotDocument;
import ca.vinaitsolution.vinasign.enveloppe.domain.DocumentRefuse;
import ca.vinaitsolution.vinasign.enveloppe.domain.EmpreinteAlteree;
import ca.vinaitsolution.vinasign.enveloppe.domain.Enveloppe;
import ca.vinaitsolution.vinasign.enveloppe.domain.EnveloppeIntrouvable;
import ca.vinaitsolution.vinasign.enveloppe.domain.EvenementEnveloppe;
import ca.vinaitsolution.vinasign.enveloppe.domain.HorsTour;
import ca.vinaitsolution.vinasign.enveloppe.domain.IdentifiantEnveloppe;
import ca.vinaitsolution.vinasign.enveloppe.domain.ModeEnveloppe;
import ca.vinaitsolution.vinasign.enveloppe.domain.QuotaAtteint;
import ca.vinaitsolution.vinasign.enveloppe.domain.ReferenceDejaUtilisee;
import ca.vinaitsolution.vinasign.enveloppe.domain.SaisieInvalide;
import ca.vinaitsolution.vinasign.enveloppe.domain.Signataire;
import ca.vinaitsolution.vinasign.enveloppe.domain.StatutEnveloppe;
import ca.vinaitsolution.vinasign.enveloppe.domain.StatutSignataire;
import ca.vinaitsolution.vinasign.enveloppe.domain.TransitionEnveloppeInterdite;
import ca.vinaitsolution.vinasign.enveloppe.domain.VersionDocument;
import ca.vinaitsolution.vinasign.enveloppe.domain.VersionImmuable;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class EnveloppeDomaineTest {

    private static final Instant T0 = Instant.parse("2026-11-22T20:00:00Z");
    private static final Instant ECHEANCE = T0.plusSeconds(3600);
    private static final IdentifiantOrganisation ORG = new IdentifiantOrganisation(UUID.randomUUID());

    private static Enveloppe brouillon(ModeEnveloppe mode) {
        return Enveloppe.creer(new IdentifiantEnveloppe(UUID.randomUUID()), ORG, "vs-app", " REF-1 ", mode, ECHEANCE, T0);
    }

    private static Signataire signataire(String nom, int ordre) {
        return Signataire.nouveau(UUID.randomUUID(), nom, nom.toLowerCase() + "@exemple.ca", "fr", "invite", ordre, null);
    }

    private static Enveloppe envoyee(Signataire... s) {
        return brouillon(ModeEnveloppe.EMPREINTE).definirSignataires(List.of(s), T0).envoyer(false, T0.plusSeconds(1));
    }

    @Test
    void une_enveloppe_nait_en_brouillon_avec_une_reference_epuree() {
        Enveloppe e = brouillon(ModeEnveloppe.DOCUMENT);
        assertEquals(StatutEnveloppe.BROUILLON, e.statut());
        assertEquals("REF-1", e.referenceExterne());
        assertTrue(e.signataires().isEmpty());
        assertTrue(e.peuventAgir().isEmpty(), "rien à signer avant l'envoi");
    }

    @Test
    void une_echeance_passee_ou_une_reference_invalide_est_refusee() {
        IdentifiantEnveloppe id = new IdentifiantEnveloppe(UUID.randomUUID());
        assertEquals("echeance-passee", assertThrows(SaisieInvalide.class,
                () -> Enveloppe.creer(id, ORG, "a", "r", ModeEnveloppe.DOCUMENT, T0, T0)).type());
        assertThrows(IllegalArgumentException.class, () -> Enveloppe.creer(id, ORG, "a", " ", ModeEnveloppe.DOCUMENT, ECHEANCE, T0));
        assertThrows(IllegalArgumentException.class, () -> Enveloppe.creer(id, ORG, "a", null, ModeEnveloppe.DOCUMENT, ECHEANCE, T0));
        assertThrows(IllegalArgumentException.class, () -> Enveloppe.creer(id, ORG, "a", "x".repeat(201), ModeEnveloppe.DOCUMENT, ECHEANCE, T0));
        assertThrows(IllegalArgumentException.class, () -> new IdentifiantEnveloppe(null));
    }

    @Test
    void une_enveloppe_incomplete_est_refusee() {
        IdentifiantEnveloppe id = new IdentifiantEnveloppe(UUID.randomUUID());
        StatutEnveloppe b = StatutEnveloppe.BROUILLON;
        ModeEnveloppe d = ModeEnveloppe.DOCUMENT;
        assertThrows(IllegalArgumentException.class, () -> new Enveloppe(null, ORG, "a", "r", d, b, null, T0, T0, T0));
        assertThrows(IllegalArgumentException.class, () -> new Enveloppe(id, null, "a", "r", d, b, null, T0, T0, T0));
        assertThrows(IllegalArgumentException.class, () -> new Enveloppe(id, ORG, null, "r", d, b, null, T0, T0, T0));
        assertThrows(IllegalArgumentException.class, () -> new Enveloppe(id, ORG, "a", "r", null, b, null, T0, T0, T0));
        assertThrows(IllegalArgumentException.class, () -> new Enveloppe(id, ORG, "a", "r", d, null, null, T0, T0, T0));
        assertThrows(IllegalArgumentException.class, () -> new Enveloppe(id, ORG, "a", "r", d, b, null, null, T0, T0));
        assertThrows(IllegalArgumentException.class, () -> new Enveloppe(id, ORG, "a", "r", d, b, null, T0, null, T0));
        assertThrows(IllegalArgumentException.class, () -> new Enveloppe(id, ORG, "a", "r", d, b, null, T0, T0, null));
        assertTrue(new Enveloppe(id, ORG, "a", "r", d, b, null, T0, T0, T0).signataires().isEmpty());
    }

    @Test
    void les_signataires_se_definissent_en_brouillon_une_fois_chacun() {
        Signataire a = signataire("Alice", 1);
        Enveloppe e = brouillon(ModeEnveloppe.DOCUMENT);
        assertEquals(List.of(a), e.definirSignataires(List.of(a), T0).signataires());
        assertEquals("signataires-invalides", assertThrows(SaisieInvalide.class, () -> e.definirSignataires(List.of(), T0)).type());
        assertThrows(SaisieInvalide.class, () -> e.definirSignataires(List.of(a, a), T0));
        List<Signataire> trop = Stream.generate(() -> signataire("S", 1)).limit(51).toList();
        assertThrows(SaisieInvalide.class, () -> e.definirSignataires(trop, T0));
        Enveloppe partie = e.definirSignataires(List.of(a), T0).envoyer(true, T0);
        assertThrows(TransitionEnveloppeInterdite.class, () -> partie.definirSignataires(List.of(a), T0));
    }

    @Test
    void l_envoi_exige_signataires_document_en_mode_document_echeance_future_et_un_brouillon() {
        Enveloppe document = brouillon(ModeEnveloppe.DOCUMENT);
        assertEquals("signataires-requis", assertThrows(SaisieInvalide.class, () -> document.envoyer(true, T0)).type());
        Enveloppe avec = document.definirSignataires(List.of(signataire("Alice", 1)), T0);
        assertEquals("document-requis", assertThrows(SaisieInvalide.class, () -> avec.envoyer(false, T0)).type());
        assertThrows(TransitionEnveloppeInterdite.class, () -> avec.envoyer(true, ECHEANCE));
        Enveloppe partie = avec.envoyer(true, T0.plusSeconds(5));
        assertEquals(StatutEnveloppe.ENVOYEE, partie.statut());
        assertEquals(T0.plusSeconds(5), partie.modifieLe());
        assertThrows(TransitionEnveloppeInterdite.class, () -> partie.envoyer(true, T0));
        assertEquals(StatutEnveloppe.ENVOYEE, brouillon(ModeEnveloppe.EMPREINTE).definirSignataires(List.of(signataire("A", 1)), T0)
                .envoyer(false, T0).statut(), "une empreinte s'envoie sans document");
    }

    @Test
    void l_ordre_decide_qui_peut_agir_et_un_signataire_ne_signe_pas_avant_son_tour() {
        Signataire alice = signataire("Alice", 1);
        Signataire bruno = signataire("Bruno", 2);
        Signataire chloe = signataire("Chloé", 2);
        Enveloppe e = envoyee(chloe, bruno, alice);
        assertEquals(List.of(alice), e.peuventAgir());
        assertThrows(HorsTour.class, () -> e.signer(bruno.id(), T0));
        Enveloppe apres = e.signer(alice.id(), T0.plusSeconds(2));
        assertEquals(List.of(bruno, chloe).stream().map(Signataire::id).toList(), apres.peuventAgir().stream().map(Signataire::id).toList(),
                "même ordre : en parallèle");
        assertEquals(StatutSignataire.SIGNE, apres.signataires().stream().filter(s -> s.id().equals(alice.id())).findFirst().orElseThrow().statut());
        assertThrows(HorsTour.class, () -> apres.signer(alice.id(), T0), "déjà signé : plus son tour");
        assertEquals("signataire-inconnu", assertThrows(SaisieInvalide.class, () -> apres.signer(UUID.randomUUID(), T0)).type());
        Enveloppe toutes = apres.signer(bruno.id(), T0).signer(chloe.id(), T0);
        assertTrue(toutes.toutesSignees());
        assertTrue(toutes.peuventAgir().isEmpty());
        assertEquals(StatutEnveloppe.ENVOYEE, toutes.statut(), "complétée seulement par la finalisation");
        assertEquals(StatutEnveloppe.COMPLETEE, toutes.completer(T0).statut());
        assertThrows(TransitionEnveloppeInterdite.class, () -> apres.completer(T0), "pas avant toutes les signatures");
        assertThrows(TransitionEnveloppeInterdite.class, () -> toutes.completer(T0).signer(alice.id(), T0));
        assertThrows(TransitionEnveloppeInterdite.class, () -> toutes.completer(T0).completer(T0), "complétée une seule fois");
    }

    @Test
    void un_refus_termine_l_enveloppe() {
        Signataire alice = signataire("Alice", 1);
        Enveloppe refusee = envoyee(alice, signataire("Bruno", 2)).refuser(alice.id(), T0);
        assertEquals(StatutEnveloppe.REFUSEE, refusee.statut());
        assertTrue(refusee.peuventAgir().isEmpty());
        assertFalse(refusee.toutesSignees());
        assertFalse(brouillon(ModeEnveloppe.DOCUMENT).toutesSignees());
        assertThrows(TransitionEnveloppeInterdite.class, () -> refusee.annuler(T0));
    }

    @Test
    void annulation_et_expiration_suivent_la_machine_d_etats() {
        assertEquals(StatutEnveloppe.ANNULEE, brouillon(ModeEnveloppe.DOCUMENT).annuler(T0).statut());
        Enveloppe partie = envoyee(signataire("Alice", 1));
        assertEquals(StatutEnveloppe.ANNULEE, partie.annuler(T0).statut());
        assertThrows(TransitionEnveloppeInterdite.class, () -> partie.annuler(T0).annuler(T0));
        assertThrows(TransitionEnveloppeInterdite.class, () -> partie.expirer(ECHEANCE.minusSeconds(1)), "pas avant l'échéance");
        Enveloppe expiree = partie.expirer(ECHEANCE);
        assertEquals(StatutEnveloppe.EXPIREE, expiree.statut());
        assertThrows(TransitionEnveloppeInterdite.class, () -> expiree.expirer(ECHEANCE));
        assertThrows(TransitionEnveloppeInterdite.class, () -> brouillon(ModeEnveloppe.DOCUMENT).expirer(ECHEANCE), "un brouillon n'expire pas");
    }

    @Test
    void les_statuts_se_lisent_par_leur_code() {
        for (StatutEnveloppe s : StatutEnveloppe.values()) assertEquals(s, StatutEnveloppe.depuisCode(s.code()));
        for (StatutSignataire s : StatutSignataire.values()) assertEquals(s, StatutSignataire.depuisCode(s.code()));
        for (ModeEnveloppe m : ModeEnveloppe.values()) assertEquals(m, ModeEnveloppe.depuisCode(m.code()));
        assertThrows(IllegalArgumentException.class, () -> StatutEnveloppe.depuisCode("inconnu"));
        assertFalse(StatutEnveloppe.ENVOYEE.estTerminal());
        assertTrue(StatutEnveloppe.EXPIREE.estTerminal());
    }

    @Test
    void un_signataire_est_valide_et_ne_divulgue_pas_son_courriel() {
        Signataire s = Signataire.nouveau(UUID.randomUUID(), "  Alice ", "alice@exemple.ca", "fr", "invite", 1, "  ");
        assertEquals("Alice", s.nom());
        assertNull(s.sujetExterne());
        assertEquals("ext-1", Signataire.nouveau(UUID.randomUUID(), "A", null, "fr", "t", 1, " ext-1 ").sujetExterne());
        assertFalse(s.toString().contains("alice@"));
        UUID id = UUID.randomUUID();
        StatutSignataire a = StatutSignataire.EN_ATTENTE;
        assertNull(new Signataire(id, "A", null, "fr", "t", 1, null, a, null).telephone(), "sans téléphone");
        assertNull(new Signataire(id, "A", null, "fr", "t", 1, null, a, null).avecNiveauExige(" ").niveauExige(), "niveau vide : aucun");
        assertEquals("renforce", new Signataire(id, "A", null, "fr", "t", 1, null, a, null).avecNiveauExige("renforce").niveauExige());
        assertThrows(IllegalArgumentException.class, () -> new Signataire(null, "A", null, "fr", "t", 1, null, a, null));
        assertThrows(IllegalArgumentException.class, () -> new Signataire(id, "A", null, null, "t", 1, null, a, null));
        assertThrows(IllegalArgumentException.class, () -> new Signataire(id, "A", null, " ", "t", 1, null, a, null));
        assertThrows(IllegalArgumentException.class, () -> new Signataire(id, "A", null, "fr", null, 1, null, a, null));
        assertThrows(IllegalArgumentException.class, () -> new Signataire(id, "A", null, "fr", " ", 1, null, a, null));
        assertThrows(IllegalArgumentException.class, () -> new Signataire(id, "A", null, "fr", "t", 1, null, null, null));
        assertThrows(IllegalArgumentException.class, () -> new Signataire(id, null, null, "fr", "t", 1, null, a, null));
        assertThrows(IllegalArgumentException.class, () -> new Signataire(id, "x".repeat(201), null, "fr", "t", 1, null, a, null));
        assertThrows(IllegalArgumentException.class, () -> new Signataire(id, "A", "pas-un-courriel", "fr", "t", 1, null, a, null));
        assertThrows(IllegalArgumentException.class, () -> new Signataire(id, "A", "a@" + "b".repeat(320) + ".ca", "fr", "t", 1, null, a, null));
        assertThrows(IllegalArgumentException.class, () -> new Signataire(id, "A", null, "fr", "t", 0, null, a, null));
        assertThrows(IllegalArgumentException.class, () -> new Signataire(id, "A", null, "fr", "t", 101, null, a, null));
    }

    @Test
    void un_telephone_est_normalise_au_format_international_ou_refuse() {
        Signataire s = signataire("Alice", 1);
        assertEquals("+14185550199", s.avecTelephone("+1 (418) 555-0199").telephone());
        assertNull(s.avecTelephone(" ").telephone());
        assertNull(s.telephone());
        assertThrows(IllegalArgumentException.class, () -> s.avecTelephone("418-555"));
        assertThrows(IllegalArgumentException.class, () -> s.avecTelephone("+0123456789"));
        assertFalse(s.avecTelephone("+14185550199").toString().contains("4185550199"));
        Signataire avec = s.avecTelephone("+14185550199");
        assertEquals("+14185550199", envoyee(avec).signer(avec.id(), T0).signataires().get(0).telephone(), "le téléphone survit aux transitions");
    }

    @Test
    void une_version_reconnait_ses_octets_et_rien_d_autre() throws Exception {
        byte[] contenu = "%PDF-1.7".getBytes();
        byte[] sha = MessageDigest.getInstance("SHA-256").digest(contenu);
        IdentifiantEnveloppe e = new IdentifiantEnveloppe(UUID.randomUUID());
        VersionDocument v = new VersionDocument(UUID.randomUUID(), e, "original", "cle", HexFormat.of().formatHex(sha), 8, 1, T0);
        assertTrue(v.correspond(sha));
        assertFalse(v.correspond(MessageDigest.getInstance("SHA-256").digest("autre".getBytes())));
        assertArrayEquals(contenu, v.verifier(contenu), "octets relus intacts");
        assertThrows(ca.vinaitsolution.vinasign.enveloppe.domain.EmpreinteAlteree.class, () -> v.verifier("%PDF-1.6".getBytes()), "stockage altéré");
        String h = HexFormat.of().formatHex(sha);
        UUID id = UUID.randomUUID();
        assertThrows(IllegalArgumentException.class, () -> new VersionDocument(null, e, "o", "c", h, 1, 1, T0));
        assertThrows(IllegalArgumentException.class, () -> new VersionDocument(id, null, "o", "c", h, 1, 1, T0));
        assertThrows(IllegalArgumentException.class, () -> new VersionDocument(id, e, null, "c", h, 1, 1, T0));
        assertThrows(IllegalArgumentException.class, () -> new VersionDocument(id, e, " ", "c", h, 1, 1, T0));
        assertThrows(IllegalArgumentException.class, () -> new VersionDocument(id, e, "o", null, h, 1, 1, T0));
        assertThrows(IllegalArgumentException.class, () -> new VersionDocument(id, e, "o", " ", h, 1, 1, T0));
        assertThrows(IllegalArgumentException.class, () -> new VersionDocument(id, e, "o", "c", null, 1, 1, T0));
        assertThrows(IllegalArgumentException.class, () -> new VersionDocument(id, e, "o", "c", "ABC", 1, 1, T0));
        assertThrows(IllegalArgumentException.class, () -> new VersionDocument(id, e, "o", "c", h, 0, 1, T0));
        assertThrows(IllegalArgumentException.class, () -> new VersionDocument(id, e, "o", "c", h, 1, 0, T0));
        assertThrows(IllegalArgumentException.class, () -> new VersionDocument(id, e, "o", "c", h, 1, 1, null));
    }

    @Test
    void un_depot_n_accepte_qu_un_objet_avant_son_echeance() {
        IdentifiantEnveloppe e = new IdentifiantEnveloppe(UUID.randomUUID());
        DepotDocument d = new DepotDocument(UUID.randomUUID(), e, "j", "cle", ECHEANCE, null, null);
        assertTrue(d.accepteUnObjet(T0));
        assertFalse(d.accepteUnObjet(ECHEANCE));
        assertFalse(new DepotDocument(d.id(), e, "j", "cle", ECHEANCE, T0, null).accepteUnObjet(T0));
        assertThrows(IllegalArgumentException.class, () -> new DepotDocument(null, e, "j", "c", ECHEANCE, null, null));
        assertThrows(IllegalArgumentException.class, () -> new DepotDocument(d.id(), null, "j", "c", ECHEANCE, null, null));
        assertThrows(IllegalArgumentException.class, () -> new DepotDocument(d.id(), e, null, "c", ECHEANCE, null, null));
        assertThrows(IllegalArgumentException.class, () -> new DepotDocument(d.id(), e, "j", null, ECHEANCE, null, null));
        assertThrows(IllegalArgumentException.class, () -> new DepotDocument(d.id(), e, "j", "c", null, null, null));
    }

    @Test
    void un_evenement_est_complet_et_fige_ses_donnees() {
        IdentifiantEnveloppe e = new IdentifiantEnveloppe(UUID.randomUUID());
        UUID id = UUID.randomUUID();
        assertEquals(Map.of(), new EvenementEnveloppe(id, e, ORG, "t", "a", T0, null).donnees());
        assertThrows(IllegalArgumentException.class, () -> new EvenementEnveloppe(null, e, ORG, "t", "a", T0, null));
        assertThrows(IllegalArgumentException.class, () -> new EvenementEnveloppe(id, null, ORG, "t", "a", T0, null));
        assertThrows(IllegalArgumentException.class, () -> new EvenementEnveloppe(id, e, null, "t", "a", T0, null));
        assertThrows(IllegalArgumentException.class, () -> new EvenementEnveloppe(id, e, ORG, null, "a", T0, null));
        assertThrows(IllegalArgumentException.class, () -> new EvenementEnveloppe(id, e, ORG, " ", "a", T0, null));
        assertThrows(IllegalArgumentException.class, () -> new EvenementEnveloppe(id, e, ORG, "t", null, T0, null));
        assertThrows(IllegalArgumentException.class, () -> new EvenementEnveloppe(id, e, ORG, "t", "a", null, null));
    }

    static Stream<Arguments> erreurs() {
        return Stream.of(Arguments.of(new ApplicationRequise(), ErreurMetier.Nature.INTERDIT, "application-requise"),
                Arguments.of(new DocumentRefuse("document-type", "m"), ErreurMetier.Nature.INVALIDE, "document-type"),
                Arguments.of(new EnveloppeIntrouvable(), ErreurMetier.Nature.INTROUVABLE, "enveloppe-introuvable"),
                Arguments.of(new HorsTour(), ErreurMetier.Nature.CONFLIT, "ordre-de-signature"),
                Arguments.of(new QuotaAtteint(), ErreurMetier.Nature.INTERDIT, "quota-enveloppes-atteint"),
                Arguments.of(new ReferenceDejaUtilisee(), ErreurMetier.Nature.CONFLIT, "reference-externe-deja-utilisee"),
                Arguments.of(new VersionImmuable(), ErreurMetier.Nature.CONFLIT, "version-immuable"));
    }

    @ParameterizedTest
    @MethodSource("erreurs")
    void chaque_erreur_a_sa_nature_et_son_type(ErreurMetier e, ErreurMetier.Nature nature, String type) {
        assertEquals(nature, e.nature());
        assertEquals(type, e.type());
    }

    @Test
    void une_empreinte_alteree_est_une_erreur_d_exploitation() {
        assertTrue(new EmpreinteAlteree().getMessage().contains("empreinte"));
    }
}
