package ca.vinaitsolution.vinasign.organisation;

import static org.junit.jupiter.api.Assertions.*;

import ca.vinaitsolution.vinasign.organisation.domain.DomaineNonProuve;
import ca.vinaitsolution.vinasign.organisation.domain.Inscription;
import ca.vinaitsolution.vinasign.organisation.domain.Inscription.Etat;
import ca.vinaitsolution.vinasign.organisation.domain.Inscription.Politique;
import ca.vinaitsolution.vinasign.organisation.domain.InscriptionInvalide;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** VS-122 — la demande avance selon la politique : courriel, domaine prouvé, validation ; jamais d'étape sautée. */
class InscriptionDomaineTest {

    private static final Instant T = Instant.parse("2026-11-22T12:00:00Z");
    private static final Politique BAC_A_SABLE = new Politique(false, false);
    private static final Politique PRODUCTION = new Politique(true, true);

    private static Inscription demande(String courriel, String domaine, Politique p) {
        return Inscription.demander(UUID.randomUUID(), new Inscription.Demande(" Clinique Nord ", courriel, domaine, "fr", "e".repeat(64),
                T.plusSeconds(3600), "vinasign-defi"), p, T);
    }

    @Test
    void au_bac_a_sable_le_courriel_confirme_suffit() {
        Inscription i = demande(" Ana@Exemple.CA ", null, BAC_A_SABLE);
        assertEquals("Clinique Nord", i.nomOrganisation());
        assertEquals("ana@exemple.ca", i.courriel());
        assertEquals(Etat.COURRIEL_A_CONFIRMER, i.etat());
        assertNull(i.nomEnregistrement());
        Inscription acceptee = i.confirmerCourriel(BAC_A_SABLE, T.plusSeconds(60));
        assertTrue(acceptee.estAcceptee());
        assertThrows(InscriptionInvalide.class, () -> acceptee.confirmerCourriel(BAC_A_SABLE, T), "une seule confirmation");
        assertEquals(Etat.A_VALIDER, demande("b@exemple.ca", null, new Politique(false, true)).confirmerCourriel(new Politique(false, true), T).etat());
    }

    @Test
    void en_production_le_domaine_se_prouve_puis_la_plateforme_valide() {
        Inscription i = demande("ana@rh.exemple.ca", "Exemple.ca", PRODUCTION);
        assertEquals("_vinasign-verification.exemple.ca", i.nomEnregistrement());
        Inscription aProuver = i.confirmerCourriel(PRODUCTION, T);
        assertEquals(Etat.DOMAINE_A_PROUVER, aProuver.etat());
        DomaineNonProuve absent = assertThrows(DomaineNonProuve.class, () -> aProuver.prouverDomaine(false, PRODUCTION, T));
        assertTrue(absent.getMessage().contains("vinasign-defi"));
        Inscription aValider = aProuver.prouverDomaine(true, PRODUCTION, T);
        assertEquals(Etat.A_VALIDER, aValider.etat());
        assertTrue(aValider.valider(T).estAcceptee());
        assertEquals(Etat.ACCEPTEE, aProuver.prouverDomaine(true, new Politique(true, false), T).etat(), "sans validation exigée");
        assertThrows(InscriptionInvalide.class, () -> i.valider(T), "pas avant la preuve");
    }

    @Test
    void une_demande_se_refuse_une_fois_avec_un_motif_borne() {
        Inscription r = demande("a@exemple.ca", null, BAC_A_SABLE).refuser(" " + "x".repeat(600) + " ", T);
        assertEquals(Etat.REFUSEE, r.etat());
        assertEquals(500, r.motifRefus().length());
        assertThrows(InscriptionInvalide.class, () -> r.refuser("encore", T));
        assertNull(demande("a@exemple.ca", null, BAC_A_SABLE).refuser("  ", T).motifRefus());
        assertNull(demande("a@exemple.ca", null, BAC_A_SABLE).refuser(null, T).motifRefus());
        Inscription acceptee = demande("a@exemple.ca", null, BAC_A_SABLE).confirmerCourriel(BAC_A_SABLE, T);
        assertThrows(InscriptionInvalide.class, () -> acceptee.refuser("trop tard", T));
    }

    @Test
    void une_demande_mal_formee_ou_un_lien_expire_est_refuse() {
        assertThrows(InscriptionInvalide.class, () -> Inscription.demander(UUID.randomUUID(), new Inscription.Demande(null, "a@exemple.ca", null, "fr",
                "e", T, "d"), BAC_A_SABLE, T));
        assertThrows(InscriptionInvalide.class, () -> Inscription.demander(UUID.randomUUID(), new Inscription.Demande("x".repeat(201), "a@exemple.ca", null,
                "fr", "e", T, "d"), BAC_A_SABLE, T));
        assertThrows(InscriptionInvalide.class, () -> demande(null, null, BAC_A_SABLE));
        assertThrows(InscriptionInvalide.class, () -> demande("pas-un-courriel", null, BAC_A_SABLE));
        assertThrows(InscriptionInvalide.class, () -> demande("a".repeat(64) + "@" + "b".repeat(260) + ".ca", null, BAC_A_SABLE));
        assertThrows(InscriptionInvalide.class, () -> demande("a@exemple.ca", null, PRODUCTION), "domaine exigé en production");
        assertThrows(InscriptionInvalide.class, () -> demande("a@exemple.ca", " ", PRODUCTION));
        assertThrows(InscriptionInvalide.class, () -> demande("a@autre.ca", "exemple.ca", PRODUCTION), "courriel hors du domaine");
        assertThrows(InscriptionInvalide.class, () -> demande("a@pasexemple.ca", "exemple.ca", PRODUCTION), "suffixe sans point");
        assertThrows(InscriptionInvalide.class, () -> demande("a@exemple", "exemple", PRODUCTION), "courriel sans domaine qualifié");
        assertThrows(InscriptionInvalide.class, () -> demande("a@exemple.c", "exemple.c", PRODUCTION), "domaine mal formé, courriel valide");
        Inscription i = demande("a@exemple.ca", null, BAC_A_SABLE);
        assertThrows(InscriptionInvalide.class, () -> i.confirmerCourriel(BAC_A_SABLE, T.plusSeconds(3600)), "lien expiré");
        assertThrows(InscriptionInvalide.class, () -> i.prouverDomaine(true, PRODUCTION, T), "pas avant la confirmation");
    }

    @Test
    void les_etapes_se_lisent_par_leur_code_et_une_inscription_incomplete_est_refusee() {
        for (Etat e : Etat.values()) {
            assertEquals(e, Etat.deCode(e.code()));
        }
        assertThrows(IllegalArgumentException.class, () -> Etat.deCode("x"));
        UUID u = UUID.randomUUID();
        assertThrows(IllegalArgumentException.class, () -> new Inscription(null, "n", "c", null, "fr", Etat.A_VALIDER, "e", T, "d", null, T, T));
        assertThrows(IllegalArgumentException.class, () -> new Inscription(u, "n", "c", null, "fr", null, "e", T, "d", null, T, T));
        assertThrows(IllegalArgumentException.class, () -> new Inscription(u, "n", "c", null, "fr", Etat.A_VALIDER, null, T, "d", null, T, T));
        assertThrows(IllegalArgumentException.class, () -> new Inscription(u, "n", "c", null, "fr", Etat.A_VALIDER, "e", null, "d", null, T, T));
        assertThrows(IllegalArgumentException.class, () -> new Inscription(u, "n", "c", null, "fr", Etat.A_VALIDER, "e", T, null, null, T, T));
        assertThrows(IllegalArgumentException.class, () -> new Inscription(u, "n", "c", null, "fr", Etat.A_VALIDER, "e", T, "d", null, null, T));
        assertThrows(IllegalArgumentException.class, () -> new Inscription(u, "n", "c", null, "fr", Etat.A_VALIDER, "e", T, "d", null, T, null));
    }
}
