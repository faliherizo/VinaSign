--liquibase formatted sql

--changeset vinasign:035-reference-par-organisation splitStatements:false
--comment: VS-ADR-009 — l'espace « portail » est partagé par toutes les organisations : l'unicité de la référence externe se
--         juge dans l'organisation et l'application, pas dans l'application seule (dont l'identifiant est unique par ailleurs).
ALTER TABLE enveloppe DROP CONSTRAINT uk_enveloppe_reference;
ALTER TABLE enveloppe ADD CONSTRAINT uk_enveloppe_reference UNIQUE (tenant_id, client_id, reference_externe);
--rollback ALTER TABLE enveloppe DROP CONSTRAINT uk_enveloppe_reference; ALTER TABLE enveloppe ADD CONSTRAINT uk_enveloppe_reference UNIQUE (client_id, reference_externe);
