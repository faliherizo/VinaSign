package ca.vinaitsolution.vinasign.session;

import static org.junit.jupiter.api.Assertions.*;

import ca.vinaitsolution.vinasign.commun.domain.ErreurMetier;
import ca.vinaitsolution.vinasign.session.domain.Consentement;
import ca.vinaitsolution.vinasign.session.domain.ConsentementRequis;
import ca.vinaitsolution.vinasign.session.domain.LienConsomme;
import ca.vinaitsolution.vinasign.session.domain.LienInvalide;
import ca.vinaitsolution.vinasign.session.domain.LienResolu;
import ca.vinaitsolution.vinasign.session.domain.LienSignature;
import ca.vinaitsolution.vinasign.session.domain.SessionSignataire;
import ca.vinaitsolution.vinasign.session.domain.VersionConsentementPerimee;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SessionDomaineTest {

    private static final Instant T0 = Instant.parse("2026-11-22T21:00:00Z");
    private static final Instant FIN = T0.plusSeconds(3600);
    private static final UUID E = UUID.randomUUID();
    private static final UUID S = UUID.randomUUID();
    private static final String H = "a".repeat(64);

    private static LienSignature lien(int max) {
        return LienSignature.emettre(UUID.randomUUID(), E, S, H, FIN, max);
    }

    @Test
    void un_lien_s_ouvre_jusqu_a_son_usage_maximal_puis_est_refuse() {
        LienSignature l = lien(2).ouvrir(T0).ouvrir(T0);
        assertEquals(2, l.utilisations());
        assertThrows(LienInvalide.class, () -> l.ouvrir(T0));
    }

    @Test
    void un_lien_expire_ou_revoque_est_refuse_sans_dire_pourquoi() {
        assertThrows(LienInvalide.class, () -> lien(5).ouvrir(FIN));
        LienSignature revoque = lien(5).revoquer(T0);
        assertEquals(T0, revoque.revoqueLe());
        assertSame(revoque, revoque.revoquer(T0.plusSeconds(1)), "une révocation ne se répète pas");
        assertEquals("lien-invalide", assertThrows(LienInvalide.class, () -> revoque.ouvrir(T0)).type());
    }

    @Test
    void apres_la_signature_le_lien_est_consomme_et_le_rejouer_rend_410() {
        LienSignature consomme = lien(5).ouvrir(T0).consommer(T0.plusSeconds(5));
        LienConsomme e = assertThrows(LienConsomme.class, () -> consomme.ouvrir(T0.plusSeconds(6)));
        assertEquals(ErreurMetier.Nature.PERIME, e.nature());
        assertThrows(LienConsomme.class, () -> consomme.consommer(T0));
        assertSame(consomme, consomme.revoquer(T0), "un lien consommé ne se révoque plus");
    }

    @Test
    void les_objets_incomplets_sont_refuses() {
        UUID id = UUID.randomUUID();
        assertThrows(IllegalArgumentException.class, () -> new LienSignature(null, E, S, H, FIN, 0, 1, null, null, null));
        assertThrows(IllegalArgumentException.class, () -> new LienSignature(id, null, S, H, FIN, 0, 1, null, null, null));
        assertThrows(IllegalArgumentException.class, () -> new LienSignature(id, E, null, H, FIN, 0, 1, null, null, null));
        assertThrows(IllegalArgumentException.class, () -> new LienSignature(id, E, S, null, FIN, 0, 1, null, null, null));
        assertThrows(IllegalArgumentException.class, () -> new LienSignature(id, E, S, H, null, 0, 1, null, null, null));
        assertThrows(IllegalArgumentException.class, () -> new LienSignature(id, E, S, H, FIN, 0, 0, null, null, null));
        assertThrows(IllegalArgumentException.class, () -> new LienSignature(id, E, S, H, FIN, -1, 1, null, null, null));
        assertThrows(IllegalArgumentException.class, () -> new LienResolu(null, UUID.randomUUID()));
        assertThrows(IllegalArgumentException.class, () -> new LienResolu(lien(1), null));
        assertThrows(IllegalArgumentException.class, () -> new SessionSignataire(null, E, E, S, H, FIN));
        assertThrows(IllegalArgumentException.class, () -> new SessionSignataire(id, null, E, S, H, FIN));
        assertThrows(IllegalArgumentException.class, () -> new SessionSignataire(id, E, null, S, H, FIN));
        assertThrows(IllegalArgumentException.class, () -> new SessionSignataire(id, E, E, null, H, FIN));
        assertThrows(IllegalArgumentException.class, () -> new SessionSignataire(id, E, E, S, null, FIN));
        assertThrows(IllegalArgumentException.class, () -> new SessionSignataire(id, E, E, S, H, null));
    }

    @Test
    void une_session_est_valide_jusqu_a_son_echeance() {
        SessionSignataire s = new SessionSignataire(UUID.randomUUID(), E, E, S, H, FIN);
        assertTrue(s.valideA(T0));
        assertFalse(s.valideA(FIN));
    }

    @Test
    void un_consentement_porte_version_empreinte_et_heure() {
        UUID id = UUID.randomUUID();
        assertEquals("v1", new Consentement(id, E, S, "v1", H, T0).versionTexte());
        assertThrows(IllegalArgumentException.class, () -> new Consentement(null, E, S, "v1", H, T0));
        assertThrows(IllegalArgumentException.class, () -> new Consentement(id, null, S, "v1", H, T0));
        assertThrows(IllegalArgumentException.class, () -> new Consentement(id, E, null, "v1", H, T0));
        assertThrows(IllegalArgumentException.class, () -> new Consentement(id, E, S, null, H, T0));
        assertThrows(IllegalArgumentException.class, () -> new Consentement(id, E, S, " ", H, T0));
        assertThrows(IllegalArgumentException.class, () -> new Consentement(id, E, S, "v1", null, T0));
        assertThrows(IllegalArgumentException.class, () -> new Consentement(id, E, S, "v1", "ABC", T0));
        assertThrows(IllegalArgumentException.class, () -> new Consentement(id, E, S, "v1", H, null));
        assertEquals("consentement-requis", new ConsentementRequis().type());
        assertEquals(ErreurMetier.Nature.CONFLIT, new VersionConsentementPerimee().nature());
    }

    @Test
    void une_session_integree_ne_s_ouvre_qu_une_fois_et_garde_ses_actions() {
        LienSignature l = LienSignature.integree(UUID.randomUUID(), E, S, H, FIN, java.util.Set.of("signer"));
        assertTrue(l.estIntegree());
        assertEquals(java.util.Set.of(), new LienSignature(UUID.randomUUID(), E, S, H, FIN, 0, 1, null, null, null).actions(), "sans action : aucune");
        assertFalse(LienSignature.emettre(UUID.randomUUID(), E, S, H, FIN, 50).estIntegree(), "un lien d'invitation n'est jamais intégré");
        LienSignature ouvert = l.ouvrir(T0);
        assertEquals(java.util.Set.of("signer"), ouvert.actions());
        assertThrows(LienInvalide.class, () -> ouvert.ouvrir(T0));
        assertEquals(java.util.Set.of(), lien(1).actions());
        assertEquals("ordre-de-signature", new ca.vinaitsolution.vinasign.session.domain.SignataireHorsTour().type());
    }
}
