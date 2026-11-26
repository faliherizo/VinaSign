package ca.vinaitsolution.vinasign.commun.application.port;

import ca.vinaitsolution.vinasign.commun.domain.IdentifiantOrganisation;
import java.util.Optional;

/** Plan souscrit par une organisation : code du référentiel {@code plan}. */
public interface PlanOrganisations {

    Optional<String> plan(IdentifiantOrganisation organisation);
}
