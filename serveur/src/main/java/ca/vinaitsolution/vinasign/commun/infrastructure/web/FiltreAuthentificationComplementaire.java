package ca.vinaitsolution.vinasign.commun.infrastructure.web;

import jakarta.servlet.Filter;

/**
 * Authentification apportée par un contexte (ex. session du signataire) et placée par la chaîne de sécurité avant la
 * vérification des jetons : la chaîne ne connaît que ce type, jamais le contexte qui le fournit.
 */
public interface FiltreAuthentificationComplementaire extends Filter {
}
