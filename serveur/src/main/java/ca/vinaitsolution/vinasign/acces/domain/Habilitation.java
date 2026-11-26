package ca.vinaitsolution.vinasign.acces.domain;

import ca.vinaitsolution.vinasign.commun.domain.IdentifiantOrganisation;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Ce qu'un utilisateur authentifié peut faire dans cette session : son organisation et les rôles effectivement accordés. */
public record Habilitation(UUID utilisateur, Optional<IdentifiantOrganisation> organisation, Set<String> roles) {

    public Habilitation {
        roles = Set.copyOf(roles);
    }
}
