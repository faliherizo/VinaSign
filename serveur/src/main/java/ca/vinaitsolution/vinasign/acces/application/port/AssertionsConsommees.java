package ca.vinaitsolution.vinasign.acces.application.port;

import ca.vinaitsolution.vinasign.acces.domain.IdentifiantClient;
import java.time.Instant;

/** Registre anti-rejeu des assertions client (RFC 7523, § 3). */
public interface AssertionsConsommees {

    /** Consomme l'identifiant d'assertion ; faux s'il a déjà servi (rejeu). */
    boolean consommer(String jti, IdentifiantClient client, Instant expireLe);

    /** Oublie les assertions expirées, qui ne peuvent plus être rejouées ; rend le nombre retiré. */
    int purger(Instant maintenant);
}
