package ca.vinaitsolution.vinasign.acces;

import static org.junit.jupiter.api.Assertions.*;

import ca.vinaitsolution.vinasign.acces.application.AuthentifierUtilisateur;
import ca.vinaitsolution.vinasign.acces.domain.Courriel;
import ca.vinaitsolution.vinasign.acces.domain.IdentiteExterne;
import ca.vinaitsolution.vinasign.acces.domain.UtilisateurPortail;
import ca.vinaitsolution.vinasign.commun.domain.IdentifiantOrganisation;
import ca.vinaitsolution.vinasign.commun.domain.Libelles;
import ca.vinaitsolution.vinasign.referentiel.domain.CodeReferentiel;
import ca.vinaitsolution.vinasign.referentiel.domain.TypeReferentiel;
import ca.vinaitsolution.vinasign.referentiel.domain.ValeurReferentiel;
import ca.vinaitsolution.vinasign.support.ReferentielsEnMemoire;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AuthentifierUtilisateurTest {

    private static ValeurReferentiel role(String code, boolean mfa, boolean actif) {
        return new ValeurReferentiel(new CodeReferentiel(code), Libelles.de(Map.of("fr", code)), 1, Map.of("exigeMfa", String.valueOf(mfa)), actif);
    }

    private static final TypeReferentiel ROLES = new TypeReferentiel(new CodeReferentiel("role-portail"), Libelles.de(Map.of("fr", "Rôles")),
            List.of(role("admin", true, true), role("lecteur", false, true), role("retire", false, false)));

    private final UtilisateurPortail utilisateur = UtilisateurPortail.inviter(UUID.randomUUID(), new IdentifiantOrganisation(UUID.randomUUID()),
            new Courriel("u@exemple.ca"), Set.of("admin", "lecteur", "retire", "inconnu"));

    private AuthentifierUtilisateur authentification(Optional<UtilisateurPortail> trouve) {
        return new AuthentifierUtilisateur(i -> trouve, new ReferentielsEnMemoire().avec(ROLES), Set.of("mfa", "hwk"));
    }

    private static IdentiteExterne identite(String... amr) {
        return new IdentiteExterne("https://idp", "s", Optional.empty(), Set.of(amr));
    }

    @Test
    void avec_mfa_tous_les_roles_actifs_sont_accordes() {
        var h = authentification(Optional.of(utilisateur)).executer(identite("pwd", "hwk")).orElseThrow();
        assertEquals(Set.of("admin", "lecteur"), h.roles(), "un rôle retiré ou inconnu du référentiel n'est jamais accordé");
        assertEquals(utilisateur.organisation(), h.organisation());
        assertEquals(utilisateur.id(), h.utilisateur());
    }

    @Test
    void sans_mfa_seuls_les_roles_qui_ne_l_exigent_pas_sont_accordes() {
        assertEquals(Set.of("lecteur"), authentification(Optional.of(utilisateur)).executer(identite("pwd")).orElseThrow().roles());
    }

    @Test
    void un_utilisateur_inconnu_ou_desactive_n_a_aucune_habilitation() {
        assertTrue(authentification(Optional.empty()).executer(identite("mfa")).isEmpty());
        assertTrue(authentification(Optional.of(utilisateur.desactiver())).executer(identite("mfa")).isEmpty());
    }

    @Test
    void sans_referentiel_des_roles_la_plateforme_est_mal_installee() {
        AuthentifierUtilisateur sans = new AuthentifierUtilisateur(i -> Optional.of(utilisateur), new ReferentielsEnMemoire(), Set.of());
        assertThrows(IllegalStateException.class, () -> sans.executer(identite()));
    }
}
