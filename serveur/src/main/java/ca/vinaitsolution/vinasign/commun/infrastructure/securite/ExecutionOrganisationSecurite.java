package ca.vinaitsolution.vinasign.commun.infrastructure.securite;

import ca.vinaitsolution.vinasign.commun.application.port.ExecutionOrganisation;
import ca.vinaitsolution.vinasign.commun.domain.IdentifiantOrganisation;
import java.util.function.Supplier;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/** Pose une authentification de tâche le temps de l'action, dans une transaction neuve (la connexion prend l'organisation). */
@Component
class ExecutionOrganisationSecurite implements ExecutionOrganisation {

    private final TransactionTemplate transaction;

    ExecutionOrganisationSecurite(TransactionTemplate transaction) {
        this.transaction = transaction;
    }

    @Override
    public <T> T pour(IdentifiantOrganisation organisation, String acteur, Supplier<T> action) {
        SecurityContext precedent = SecurityContextHolder.getContext();
        SecurityContext tache = SecurityContextHolder.createEmptyContext();
        tache.setAuthentication(new AuthentificationTache(organisation, acteur));
        SecurityContextHolder.setContext(tache);
        try {
            return transaction.execute(etat -> action.get());
        } finally {
            SecurityContextHolder.setContext(precedent);
        }
    }
}
