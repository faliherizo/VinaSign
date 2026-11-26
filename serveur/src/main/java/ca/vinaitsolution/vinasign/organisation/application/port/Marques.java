package ca.vinaitsolution.vinasign.organisation.application.port;

import ca.vinaitsolution.vinasign.organisation.domain.Marque;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** Marques des organisations (VS-123), sous la RLS de l'organisation. */
public interface Marques {

    /** {@code SousDomainePris} si le sous-domaine appartient déjà à une autre organisation. */
    void enregistrer(UUID organisation, Marque marque, Instant le);

    Optional<Marque> de(UUID organisation);
}
