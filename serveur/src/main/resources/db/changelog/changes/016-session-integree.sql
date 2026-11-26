--liquibase formatted sql

--changeset vinasign:016-1-session-integree
--comment: VS-046 — session intégrée : lien à usage unique limité à des actions (référentiel action-signataire).
ALTER TABLE lien_signature ADD COLUMN actions TEXT[] NOT NULL DEFAULT '{}';
INSERT INTO referentiel_type (code, libelles) VALUES ('action-signataire', '{"fr":"Action du signataire","en":"Signer action"}');
INSERT INTO referentiel_valeur (type_id, code, libelles, ordre, attributs)
SELECT t.id, v.code, v.libelles::jsonb, v.ordre, '{}'::jsonb FROM referentiel_type t JOIN (VALUES
  ('action-signataire', 'consulter', '{"fr":"Consulter le document","en":"View the document"}', 1),
  ('action-signataire', 'signer',    '{"fr":"Signer","en":"Sign"}', 2),
  ('action-signataire', 'refuser',   '{"fr":"Refuser","en":"Decline"}', 3)
) AS v(type, code, libelles, ordre) ON v.type = t.code;
--rollback DELETE FROM referentiel_type WHERE code = 'action-signataire'; ALTER TABLE lien_signature DROP COLUMN actions;
