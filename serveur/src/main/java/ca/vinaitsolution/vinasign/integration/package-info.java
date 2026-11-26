/**
 * Contexte « integration » : Webhooks signés, idempotence, connecteur MCP (VS-090 à VS-095, VS-ADR-004).
 * Découpage imposé : {@code domain} (Java pur), {@code application} (cas d'utilisation, ports),
 * {@code infrastructure} (adaptateurs). Voir docs/architecture/02-Dossier-d-architecture.md, § 3.
 */
package ca.vinaitsolution.vinasign.integration;
