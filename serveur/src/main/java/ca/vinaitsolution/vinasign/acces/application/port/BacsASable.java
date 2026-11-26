package ca.vinaitsolution.vinasign.acces.application.port;

import ca.vinaitsolution.vinasign.acces.domain.IdentifiantClient;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** Rattachement d'une application de bac à sable à une organisation neuve (VS-095) ; vide si l'application n'est pas réinitialisable. */
public interface BacsASable {

    Optional<UUID> reinitialiser(IdentifiantClient client, UUID nouvelleOrganisation, Instant le);
}
