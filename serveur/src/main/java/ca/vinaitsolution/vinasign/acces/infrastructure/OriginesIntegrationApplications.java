package ca.vinaitsolution.vinasign.acces.infrastructure;

import ca.vinaitsolution.vinasign.acces.application.port.ApplicationsClientes;
import ca.vinaitsolution.vinasign.acces.domain.IdentifiantClient;
import ca.vinaitsolution.vinasign.acces.domain.OrigineIntegration;
import ca.vinaitsolution.vinasign.commun.application.port.OriginesIntegration;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/** Origines d'intégration d'une application <em>active</em>, lues dans le registre des applications clientes. */
@Component
class OriginesIntegrationApplications implements OriginesIntegration {

    private final ApplicationsClientes applications;

    OriginesIntegrationApplications(ApplicationsClientes applications) {
        this.applications = applications;
    }

    @Override
    public Set<String> de(String clientApplication) {
        if (clientApplication == null || clientApplication.isBlank()) {
            return Set.of();
        }
        return applications.activeParClient(new IdentifiantClient(clientApplication))
                .map(a -> a.origines().stream().map(OrigineIntegration::valeur).collect(Collectors.toUnmodifiableSet())).orElse(Set.of());
    }
}
