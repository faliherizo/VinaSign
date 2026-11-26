package ca.vinaitsolution.vinasign.commun.application.port;

import ca.vinaitsolution.vinasign.commun.domain.IdentifiantOrganisation;
import java.util.function.Supplier;

/**
 * Exécution d'une tâche de la plateforme au nom d'une organisation (relances, expirations) : dans une transaction soumise
 * à la RLS de cette organisation, avec un acteur de la plateforme ; le contexte d'origine est rétabli ensuite.
 */
public interface ExecutionOrganisation {

    <T> T pour(IdentifiantOrganisation organisation, String acteur, Supplier<T> action);
}
