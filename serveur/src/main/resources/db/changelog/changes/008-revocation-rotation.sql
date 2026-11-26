--liquibase formatted sql

--changeset vinasign:008-1-actions-audit-applications
--comment: VS-013 — révocation et rotation des clés d'une application, consignées.
INSERT INTO referentiel_valeur (type_id, code, libelles, ordre, attributs)
SELECT t.id, v.code, v.libelles::jsonb, v.ordre, '{}'::jsonb FROM referentiel_type t JOIN (VALUES
  ('application-revoquee',         '{"fr":"Application cliente révoquée","en":"Client application revoked"}', 11),
  ('cles-application-renouvelees', '{"fr":"Clés d''application renouvelées","en":"Application keys rotated"}', 12)
) AS v(code, libelles, ordre) ON t.code = 'action-audit';
--rollback DELETE FROM referentiel_valeur WHERE code IN ('application-revoquee', 'cles-application-renouvelees');
