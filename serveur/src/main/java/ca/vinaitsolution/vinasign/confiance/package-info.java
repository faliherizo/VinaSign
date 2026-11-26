/**
 * Contexte « confiance » : Clés en HSM (port CleDeSignature), certificats de cachet par organisation, rotation et révocation (VS-031, VS-ADR-003).
 * Découpage imposé : {@code domain} (Java pur), {@code application} (cas d'utilisation, ports),
 * {@code infrastructure} (adaptateurs). Voir docs/architecture/02-Dossier-d-architecture.md, § 3.
 */
package ca.vinaitsolution.vinasign.confiance;
