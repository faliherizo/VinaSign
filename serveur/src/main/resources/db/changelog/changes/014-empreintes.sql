--liquibase formatted sql

--changeset vinasign:014-1-portee-empreintes
--comment: VS-034 — portée de la signature d'empreinte et action consignée.
INSERT INTO referentiel_valeur (type_id, code, libelles, ordre, attributs)
SELECT t.id, v.code, v.libelles::jsonb, v.ordre, '{}'::jsonb FROM referentiel_type t JOIN (VALUES
  ('portee-api', 'empreintes-signer', '{"fr":"Faire signer des empreintes","en":"Sign digests"}', 10),
  ('action-audit', 'empreinte-signee', '{"fr":"Empreinte signée","en":"Digest signed"}', 30)
) AS v(type, code, libelles, ordre) ON v.type = t.code;
--rollback DELETE FROM referentiel_valeur WHERE code IN ('empreintes-signer', 'empreinte-signee');
