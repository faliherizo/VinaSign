package ca.vinaitsolution.vinasign.organisation.infrastructure;

import ca.vinaitsolution.vinasign.commun.application.port.ControleAcces;
import ca.vinaitsolution.vinasign.commun.application.port.StatutOrganisations;
import ca.vinaitsolution.vinasign.commun.domain.ContexteAcces;
import ca.vinaitsolution.vinasign.commun.domain.RefusAcces;
import java.util.Optional;
import org.springframework.stereotype.Component;

/** Une organisation suspendue (ou inconnue) n'a plus accès à aucune API (VS-010). */
@Component
class ControleOrganisationActive implements ControleAcces {

    private final StatutOrganisations statuts;

    ControleOrganisationActive(StatutOrganisations statuts) {
        this.statuts = statuts;
    }

    @Override
    public Optional<RefusAcces> verifier(ContexteAcces contexte) {
        return contexte.organisation().filter(statuts::estBloquee)
                .map(o -> new RefusAcces("organisation-suspendue", "L'organisation est suspendue : l'accès à l'API est refusé."));
    }
}
