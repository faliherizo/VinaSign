package ca.vinaitsolution.vinasign.session.application.port;

import ca.vinaitsolution.vinasign.session.domain.VerificationIdentite;
import java.util.Optional;
import java.util.UUID;

/** Vérifications d'identité, sous la RLS de l'organisation (VS-131). */
public interface VerificationsIdentite {

    void ajouter(VerificationIdentite verification);

    void remplacer(VerificationIdentite verification);

    Optional<VerificationIdentite> parId(UUID id);

    Optional<VerificationIdentite> parReference(String reference);

    /** Hors organisation : l'organisation de cette référence, pour y exécuter la suite du rappel. */
    Optional<UUID> organisationDe(String reference);
}
