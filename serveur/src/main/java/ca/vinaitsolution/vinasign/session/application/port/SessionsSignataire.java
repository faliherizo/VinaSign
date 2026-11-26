package ca.vinaitsolution.vinasign.session.application.port;

import ca.vinaitsolution.vinasign.session.domain.SessionSignataire;
import java.util.Optional;

/** Sessions des signataires. */
public interface SessionsSignataire {

    void ajouter(SessionSignataire session);

    /** Session de ce jeton, quelle que soit l'organisation (authentification du signataire). */
    Optional<SessionSignataire> session(String empreinteJeton);
}
