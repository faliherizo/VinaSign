/**
 * Contexte « session » : Liens d'invitation, sessions intégrées, consentement, authentification du signataire : code, clé d'accès, assertion de l'application (VS-040 à VS-046, VS-ADR-007).
 * Découpage imposé : {@code domain} (Java pur), {@code application} (cas d'utilisation, ports),
 * {@code infrastructure} (adaptateurs). Voir docs/architecture/02-Dossier-d-architecture.md, § 3.
 */
package ca.vinaitsolution.vinasign.session;
