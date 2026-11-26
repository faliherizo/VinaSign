package ca.vinaitsolution.vinasign.session.application.port;

import ca.vinaitsolution.vinasign.session.domain.Consentement;
import java.util.Optional;
import java.util.UUID;

/** Consentements des signataires (ajout seul). */
public interface Consentements {

    void ajouter(Consentement consentement);

    Optional<Consentement> dernier(UUID signataire);
}
