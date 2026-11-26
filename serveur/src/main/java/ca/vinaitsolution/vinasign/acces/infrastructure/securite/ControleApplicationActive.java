package ca.vinaitsolution.vinasign.acces.infrastructure.securite;

import ca.vinaitsolution.vinasign.acces.application.port.ApplicationsClientes;
import ca.vinaitsolution.vinasign.acces.domain.IdentifiantClient;
import ca.vinaitsolution.vinasign.commun.application.port.ControleAcces;
import ca.vinaitsolution.vinasign.commun.domain.ContexteAcces;
import ca.vinaitsolution.vinasign.commun.domain.RefusAcces;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * Le jeton d'une application révoquée est refusé dès la requête suivante (VS-013), sans attendre son expiration : la
 * révocation est effective immédiatement, bien sous la minute exigée. L'espace « portail » n'est pas une application : ses
 * utilisateurs sont contrôlés par leur fournisseur d'identité et leur rôle (VS-ADR-009).
 */
@Component
class ControleApplicationActive implements ControleAcces {

    private final ApplicationsClientes applications;

    ControleApplicationActive(ApplicationsClientes applications) {
        this.applications = applications;
    }

    @Override
    public Optional<RefusAcces> verifier(ContexteAcces contexte) {
        if (contexte.estPortail()) {
            return Optional.empty();
        }
        return contexte.client().filter(client -> !estActive(client))
                .map(c -> new RefusAcces("application-revoquee", "L'application cliente est révoquée : ses jetons sont refusés."));
    }

    private boolean estActive(String client) {
        try {
            return applications.activeParClient(new IdentifiantClient(client)).isPresent();
        } catch (IllegalArgumentException malForme) {
            return false;
        }
    }
}
