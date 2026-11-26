package ca.vinaitsolution.vinasign.acces.domain;

import ca.vinaitsolution.vinasign.commun.domain.IdentifiantOrganisation;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Utilisateur du portail (VS-014), invité par son courriel puis lié à son identité OIDC à sa première connexion. Ses
 * rôles sont des codes du référentiel {@code role-portail}. Sans organisation : utilisateur de la plateforme.
 */
public record UtilisateurPortail(UUID id, Optional<IdentifiantOrganisation> organisation, Courriel courriel, Set<String> roles,
                                 boolean actif) {

    public UtilisateurPortail {
        if (id == null || courriel == null) {
            throw new IllegalArgumentException("Utilisateur incomplet.");
        }
        organisation = organisation == null ? Optional.empty() : organisation;
        if (roles == null || roles.isEmpty()) {
            throw new IllegalArgumentException("Un utilisateur a au moins un rôle.");
        }
        roles = Set.copyOf(roles);
    }

    public static UtilisateurPortail inviter(UUID id, IdentifiantOrganisation organisation, Courriel courriel, Set<String> roles) {
        return new UtilisateurPortail(id, Optional.of(organisation), courriel, roles, true);
    }

    public UtilisateurPortail avecRoles(Set<String> nouveaux) {
        exigerActif();
        return new UtilisateurPortail(id, organisation, courriel, nouveaux, actif);
    }

    public UtilisateurPortail desactiver() {
        exigerActif();
        return new UtilisateurPortail(id, organisation, courriel, roles, false);
    }

    private void exigerActif() {
        if (!actif) {
            throw new TransitionUtilisateurInterdite();
        }
    }
}
