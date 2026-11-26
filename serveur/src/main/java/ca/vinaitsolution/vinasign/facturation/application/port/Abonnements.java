package ca.vinaitsolution.vinasign.facturation.application.port;

import ca.vinaitsolution.vinasign.facturation.domain.Abonnement;
import java.util.Optional;
import java.util.UUID;

/** Abonnements, sous la RLS de l'organisation (VS-121). */
public interface Abonnements {

    void enregistrer(Abonnement abonnement);

    Optional<Abonnement> de(UUID organisation);

    /** Hors organisation : l'organisation de ce client du prestataire. */
    Optional<UUID> organisationDuClient(String client);
}
