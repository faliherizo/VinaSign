package ca.vinaitsolution.vinasign.commun.application.port;

import java.util.Set;

/**
 * Origines qu'une application cliente a déclarées pour intégrer l'interface du signataire (VS-013, VS-055) ; vide pour
 * une application inconnue ou révoquée. Lecture entre contextes : seules les origines, jamais l'application elle-même.
 */
public interface OriginesIntegration {

    Set<String> de(String clientApplication);
}
