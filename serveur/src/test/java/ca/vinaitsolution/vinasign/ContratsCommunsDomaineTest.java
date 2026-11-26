package ca.vinaitsolution.vinasign;

import static org.junit.jupiter.api.Assertions.*;

import ca.vinaitsolution.vinasign.commun.domain.EvenementMetier;
import ca.vinaitsolution.vinasign.commun.domain.IdentifiantOrganisation;
import ca.vinaitsolution.vinasign.commun.domain.IdentiteSignataire;
import ca.vinaitsolution.vinasign.integration.domain.ApplicationAttendue;
import ca.vinaitsolution.vinasign.commun.domain.ErreurMetier;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Objets du domaine commun échangés entre contextes : complets à la construction, figés ensuite. */
class ContratsCommunsDomaineTest {

    private static final IdentifiantOrganisation ORG = new IdentifiantOrganisation(UUID.randomUUID());

    @Test
    void seul_l_espace_portail_designe_un_utilisateur_du_portail() {
        var org = java.util.Optional.of(new IdentifiantOrganisation(UUID.randomUUID()));
        assertTrue(new ca.vinaitsolution.vinasign.commun.domain.ContexteAcces(org, java.util.Optional.of("portail")).estPortail());
        assertFalse(new ca.vinaitsolution.vinasign.commun.domain.ContexteAcces(org, java.util.Optional.of("vs-abc")).estPortail());
        assertFalse(ca.vinaitsolution.vinasign.commun.domain.ContexteAcces.anonyme().estPortail());
    }

    @Test
    void un_evenement_metier_a_un_type_une_organisation_et_des_attributs_figes() {
        Map<String, String> attributs = new HashMap<>(Map.of("enveloppe", "e1"));
        EvenementMetier e = new EvenementMetier("enveloppe-envoyee", ORG, attributs);
        attributs.put("intrus", "x");
        assertEquals(Map.of("enveloppe", "e1"), e.attributs());
        assertEquals(Map.of(), new EvenementMetier("t", ORG, null).attributs());
        assertThrows(IllegalArgumentException.class, () -> new EvenementMetier(null, ORG, Map.of()));
        assertThrows(IllegalArgumentException.class, () -> new EvenementMetier(" ", ORG, Map.of()));
        assertThrows(IllegalArgumentException.class, () -> new EvenementMetier("t", null, Map.of()));
    }

    @Test
    void l_identite_d_un_signataire_est_complete() {
        UUID u = UUID.randomUUID();
        assertEquals(u, new IdentiteSignataire(ORG, u, u, u).session());
        assertThrows(IllegalArgumentException.class, () -> new IdentiteSignataire(null, u, u, u));
        assertThrows(IllegalArgumentException.class, () -> new IdentiteSignataire(ORG, null, u, u));
        assertThrows(IllegalArgumentException.class, () -> new IdentiteSignataire(ORG, u, null, u));
        assertThrows(IllegalArgumentException.class, () -> new IdentiteSignataire(ORG, u, u, null));
    }

    @Test
    void les_webhooks_sont_reserves_aux_applications() {
        ApplicationAttendue e = new ApplicationAttendue();
        assertEquals("application-requise", e.type());
        assertEquals(ErreurMetier.Nature.INTERDIT, e.nature());
    }

    @Test
    void le_dossier_d_un_signataire_ne_revele_ni_courriel_ni_telephone_dans_les_journaux() {
        var s = new ca.vinaitsolution.vinasign.commun.application.dto.DossierEnveloppe.SignataireDossier(UUID.randomUUID(), "Alice", "alice@exemple.ca", "fr",
                "invite", 1, "en-attente", null, true, "+15145550000");
        assertFalse(s.toString().contains("alice@") || s.toString().contains("5550000") || s.toString().contains("Alice"), s.toString());
        assertTrue(s.toString().contains("ordre=1"));
    }
}
