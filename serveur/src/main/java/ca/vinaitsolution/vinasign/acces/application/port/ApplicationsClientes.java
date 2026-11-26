package ca.vinaitsolution.vinasign.acces.application.port;

import ca.vinaitsolution.vinasign.acces.domain.ApplicationCliente;
import ca.vinaitsolution.vinasign.acces.domain.IdentifiantClient;
import java.util.Optional;

/** Lecture des applications clientes au point de jeton (le seul besoin du serveur d'autorisation). */
public interface ApplicationsClientes {

    /**
     * Application <em>active</em> portant cet identifiant, quelle que soit son organisation : seule lecture permise avant
     * que l'organisation soit connue (authentification du client au point de jeton).
     */
    Optional<ApplicationCliente> activeParClient(IdentifiantClient client);
}
