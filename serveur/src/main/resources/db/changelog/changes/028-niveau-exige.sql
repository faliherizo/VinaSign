--liquibase formatted sql

--changeset vinasign:028-niveau-exige
--comment: VS-042, VS-043 — niveau d'authentification exigé d'un signataire (code du référentiel niveau-authentification) ; nul : aucun.
ALTER TABLE signataire ADD COLUMN niveau_exige VARCHAR(30);
--rollback ALTER TABLE signataire DROP COLUMN niveau_exige;
