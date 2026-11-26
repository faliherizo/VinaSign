package ca.vinaitsolution.vinasign.organisation.application.port;

import ca.vinaitsolution.vinasign.commun.domain.IdentifiantOrganisation;
import ca.vinaitsolution.vinasign.organisation.domain.Organisation;
import java.util.Optional;

/** Organisations de la plateforme. */
public interface Organisations {

    void ajouter(Organisation organisation);

    /** Remplace l'état d'une organisation existante ; {@code OrganisationIntrouvable} sinon. */
    void remplacer(Organisation organisation);

    Optional<Organisation> parId(IdentifiantOrganisation id);
}
