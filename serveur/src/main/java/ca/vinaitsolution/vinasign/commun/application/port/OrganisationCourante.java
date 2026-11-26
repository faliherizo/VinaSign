package ca.vinaitsolution.vinasign.commun.application.port;

import ca.vinaitsolution.vinasign.commun.domain.IdentifiantOrganisation;
import java.util.Optional;

/**
 * Organisation de la requête en cours, tirée de l'authentification (jeton), jamais d'un paramètre fourni par le client.
 * Vide pour une requête anonyme : seules les données communes sont alors visibles.
 */
public interface OrganisationCourante {

    Optional<IdentifiantOrganisation> courante();
}
