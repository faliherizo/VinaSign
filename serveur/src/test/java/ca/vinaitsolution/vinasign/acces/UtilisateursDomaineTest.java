package ca.vinaitsolution.vinasign.acces;

import static org.junit.jupiter.api.Assertions.*;

import ca.vinaitsolution.vinasign.acces.domain.Courriel;
import ca.vinaitsolution.vinasign.acces.domain.CourrielDejaInscrit;
import ca.vinaitsolution.vinasign.acces.domain.Habilitation;
import ca.vinaitsolution.vinasign.acces.domain.IdentiteExterne;
import ca.vinaitsolution.vinasign.acces.domain.OrganisationInactive;
import ca.vinaitsolution.vinasign.acces.domain.RoleNonAttribuable;
import ca.vinaitsolution.vinasign.acces.domain.TransitionUtilisateurInterdite;
import ca.vinaitsolution.vinasign.acces.domain.UtilisateurIntrouvable;
import ca.vinaitsolution.vinasign.acces.domain.UtilisateurPortail;
import ca.vinaitsolution.vinasign.commun.domain.ErreurMetier;
import ca.vinaitsolution.vinasign.commun.domain.ErreurMetier.Nature;
import ca.vinaitsolution.vinasign.commun.domain.IdentifiantOrganisation;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class UtilisateursDomaineTest {

    private static final IdentifiantOrganisation ORG = new IdentifiantOrganisation(UUID.randomUUID());
    private static final Courriel COURRIEL = new Courriel("marie@exemple.ca");

    @Test
    void un_courriel_est_epure_en_minuscules_et_ne_se_divulgue_pas_par_toString() {
        Courriel c = new Courriel("  Marie.Tremblay@Exemple.QC.ca ");
        assertEquals("marie.tremblay@exemple.qc.ca", c.valeur());
        assertFalse(c.toString().contains("marie"));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"marie", "marie@", "@exemple.ca", "marie@exemple", "marie@exemple.c", "ma rie@exemple.ca", "a@b@c.ca"})
    void un_courriel_mal_forme_est_refuse(String texte) {
        assertThrows(IllegalArgumentException.class, () -> new Courriel(texte));
    }

    @Test
    void un_courriel_demesure_est_refuse() {
        assertThrows(IllegalArgumentException.class, () -> new Courriel("a@" + "b".repeat(320) + ".ca"));
    }

    @Test
    void un_utilisateur_invite_est_actif_et_ses_roles_changent_tant_qu_il_l_est() {
        UtilisateurPortail u = UtilisateurPortail.inviter(UUID.randomUUID(), ORG, COURRIEL, Set.of("auditeur"));
        assertTrue(u.actif());
        assertEquals(Optional.of(ORG), u.organisation());
        UtilisateurPortail prep = u.avecRoles(Set.of("preparateur"));
        assertEquals(Set.of("preparateur"), prep.roles());
        UtilisateurPortail inactif = prep.desactiver();
        assertFalse(inactif.actif());
        assertThrows(TransitionUtilisateurInterdite.class, inactif::desactiver);
        assertThrows(TransitionUtilisateurInterdite.class, () -> inactif.avecRoles(Set.of("auditeur")));
    }

    @Test
    void un_utilisateur_incomplet_ou_sans_role_est_refuse_et_sans_organisation_il_est_de_la_plateforme() {
        assertThrows(IllegalArgumentException.class, () -> new UtilisateurPortail(null, Optional.empty(), COURRIEL, Set.of("r"), true));
        assertThrows(IllegalArgumentException.class, () -> new UtilisateurPortail(UUID.randomUUID(), Optional.empty(), null, Set.of("r"), true));
        assertThrows(IllegalArgumentException.class, () -> new UtilisateurPortail(UUID.randomUUID(), Optional.empty(), COURRIEL, null, true));
        assertThrows(IllegalArgumentException.class, () -> new UtilisateurPortail(UUID.randomUUID(), Optional.empty(), COURRIEL, Set.of(), true));
        assertTrue(new UtilisateurPortail(UUID.randomUUID(), null, COURRIEL, Set.of("r"), true).organisation().isEmpty());
    }

    @Test
    void une_identite_externe_exige_emetteur_et_sujet_et_complete_le_reste() {
        IdentiteExterne i = new IdentiteExterne("https://idp", "s", null, null);
        assertTrue(i.courrielVerifie().isEmpty());
        assertTrue(i.methodes().isEmpty());
        assertThrows(IllegalArgumentException.class, () -> new IdentiteExterne(null, "s", null, null));
        assertThrows(IllegalArgumentException.class, () -> new IdentiteExterne(" ", "s", null, null));
        assertThrows(IllegalArgumentException.class, () -> new IdentiteExterne("e", null, null, null));
        assertThrows(IllegalArgumentException.class, () -> new IdentiteExterne("e", " ", null, null));
    }

    @Test
    void une_habilitation_fige_ses_roles() {
        var roles = new java.util.HashSet<>(Set.of("a"));
        Habilitation h = new Habilitation(UUID.randomUUID(), Optional.of(ORG), roles);
        roles.add("b");
        assertEquals(Set.of("a"), h.roles());
    }

    static Stream<Arguments> erreurs() {
        return Stream.of(Arguments.of(new CourrielDejaInscrit(), Nature.CONFLIT, "courriel-deja-inscrit"),
                Arguments.of(new OrganisationInactive(), Nature.CONFLIT, "organisation-inactive"),
                Arguments.of(new RoleNonAttribuable(), Nature.INVALIDE, "role-non-attribuable"),
                Arguments.of(new TransitionUtilisateurInterdite(), Nature.CONFLIT, "utilisateur-desactive"),
                Arguments.of(new UtilisateurIntrouvable(), Nature.INTROUVABLE, "utilisateur-introuvable"));
    }

    @ParameterizedTest
    @MethodSource("erreurs")
    void chaque_erreur_a_sa_nature_et_son_type(ErreurMetier e, Nature nature, String type) {
        assertEquals(nature, e.nature());
        assertEquals(type, e.type());
    }
}
