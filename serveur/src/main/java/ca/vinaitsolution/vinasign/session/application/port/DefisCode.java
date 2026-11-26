package ca.vinaitsolution.vinasign.session.application.port;

import ca.vinaitsolution.vinasign.session.domain.DefiCode;
import java.util.Optional;
import java.util.UUID;

/** Défis de code des sessions de signataire (organisation courante). */
public interface DefisCode {

    void ajouter(DefiCode defi);

    void remplacer(DefiCode defi);

    /** Défi le plus récent de la session. */
    Optional<DefiCode> dernier(UUID session);

    /** Marque la session au niveau d'authentification atteint (code du référentiel niveau-authentification). */
    void elever(UUID session, String niveau);
}
