package ca.vinaitsolution.vinasign.acces.application.port;

import ca.vinaitsolution.vinasign.acces.domain.ApplicationCliente;
import ca.vinaitsolution.vinasign.acces.domain.ClesPubliques;
import ca.vinaitsolution.vinasign.acces.domain.IdentifiantClient;
import java.util.Optional;
import java.util.List;

/** Registre des applications clientes d'une organisation, pour leur administration (VS-011). */
public interface RegistreApplications {

    /**
     * Ajoute une application et réserve les empreintes de ses clés ; {@code CleDejaEnregistree} si l'une d'elles appartient
     * déjà à une application (de quelque organisation ou environnement que ce soit).
     */
    void ajouter(ApplicationCliente application, ClesPubliques cles);

    /** Applications de l'organisation courante, par nom. */
    List<ApplicationCliente> deLOrganisationCourante();

    /** Application de l'organisation courante portant cet identifiant, quel que soit son statut. */
    Optional<ApplicationCliente> deLOrganisation(IdentifiantClient client);

    /** Enregistre le nouveau statut d'une application (révocation). */
    void remplacer(ApplicationCliente application);

    /**
     * Remplace les clés d'une application et leurs empreintes réservées ; {@code CleDejaEnregistree} si une nouvelle clé
     * appartient à une autre application.
     */
    void remplacerCles(ApplicationCliente application, ClesPubliques cles);
}
