--liquibase formatted sql

--changeset vinasign:005-1-applications-clientes splitStatements:false
--comment: VS-012 (et VS-011) — applications clientes : authentification private_key_jwt (clés publiques seulement), portées tirées du référentiel portee-api. Isolées par organisation (RLS).
CREATE TABLE application_cliente (
  id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id     UUID NOT NULL,
  client_id     VARCHAR(80) NOT NULL CONSTRAINT uk_application_client_id UNIQUE,
  nom           VARCHAR(120) NOT NULL,
  environnement VARCHAR(60) NOT NULL,
  jwks          JSONB NOT NULL,
  portees       TEXT[] NOT NULL,
  statut        VARCHAR(30) NOT NULL DEFAULT 'active',
  cree_le       TIMESTAMPTZ NOT NULL DEFAULT now(),
  revoque_le    TIMESTAMPTZ
);
GRANT SELECT, INSERT, UPDATE ON application_cliente TO "${roleApplicatif}";
ALTER TABLE application_cliente ENABLE ROW LEVEL SECURITY;
CREATE POLICY organisation_acces ON application_cliente TO "${roleApplicatif}"
  USING (tenant_id = NULLIF(current_setting('app.organisation', true), '')::uuid)
  WITH CHECK (tenant_id = NULLIF(current_setting('app.organisation', true), '')::uuid);

-- Seule exception motivée à la RLS : au point de jeton, l'organisation n'est connue qu'après l'authentification du
-- client. La fonction ne rend qu'une application active désignée par son client_id, rien d'autre.
CREATE FUNCTION acces_application_active(p_client_id TEXT) RETURNS SETOF application_cliente
  LANGUAGE sql STABLE SECURITY DEFINER SET search_path = public, pg_temp
  AS 'SELECT * FROM application_cliente WHERE client_id = p_client_id AND statut = ''active''';
REVOKE ALL ON FUNCTION acces_application_active(TEXT) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION acces_application_active(TEXT) TO "${roleApplicatif}";

-- Anti-rejeu des assertions client (RFC 7523, § 3) : un jti ne sert qu'une fois jusqu'à l'expiration de l'assertion.
CREATE TABLE assertion_client_utilisee (
  jti       VARCHAR(200) PRIMARY KEY,
  client_id VARCHAR(80) NOT NULL,
  expire_le TIMESTAMPTZ NOT NULL
);
GRANT SELECT, INSERT, DELETE ON assertion_client_utilisee TO "${roleApplicatif}";
--rollback DROP TABLE assertion_client_utilisee; DROP FUNCTION acces_application_active(TEXT); DROP TABLE application_cliente;

--changeset vinasign:005-2-referentiels-acces
--comment: Portées de l'API et environnements d'application : des référentiels, comme toute liste métier.
INSERT INTO referentiel_type (code, libelles) VALUES
  ('portee-api',                '{"fr":"Portée de l''API","en":"API scope"}'),
  ('environnement-application', '{"fr":"Environnement de l''application","en":"Application environment"}'),
  ('statut-application',        '{"fr":"Statut de l''application","en":"Application status"}');
INSERT INTO referentiel_valeur (type_id, code, libelles, ordre, attributs)
SELECT t.id, v.code, v.libelles::jsonb, v.ordre, v.attributs::jsonb FROM referentiel_type t JOIN (VALUES
  ('portee-api', 'enveloppes-creer',      '{"fr":"Créer des enveloppes","en":"Create envelopes"}', 1, '{}'),
  ('portee-api', 'enveloppes-lire',       '{"fr":"Lire les enveloppes","en":"Read envelopes"}', 2, '{}'),
  ('portee-api', 'enveloppes-envoyer',    '{"fr":"Envoyer et annuler des enveloppes","en":"Send and cancel envelopes"}', 3, '{}'),
  ('portee-api', 'documents-deposer',     '{"fr":"Déposer des documents","en":"Upload documents"}', 4, '{}'),
  ('portee-api', 'documents-telecharger', '{"fr":"Télécharger des documents","en":"Download documents"}', 5, '{}'),
  ('portee-api', 'signataires-gerer',     '{"fr":"Gérer les signataires","en":"Manage signers"}', 6, '{}'),
  ('portee-api', 'sessions-creer',        '{"fr":"Créer des sessions de signature intégrées","en":"Create embedded signing sessions"}', 7, '{}'),
  ('portee-api', 'preuves-lire',          '{"fr":"Lire les dossiers de preuve","en":"Read evidence"}', 8, '{}'),
  ('portee-api', 'webhooks-gerer',        '{"fr":"Gérer les webhooks","en":"Manage webhooks"}', 9, '{}'),
  ('environnement-application', 'bac-a-sable', '{"fr":"Bac à sable","en":"Sandbox"}', 1, '{"secretPartageAccepte":"false"}'),
  ('environnement-application', 'production',  '{"fr":"Production","en":"Production"}', 2, '{"secretPartageAccepte":"false"}'),
  ('statut-application', 'active',   '{"fr":"Active","en":"Active"}', 1, '{}'),
  ('statut-application', 'revoquee', '{"fr":"Révoquée","en":"Revoked"}', 2, '{}')
) AS v(type, code, libelles, ordre, attributs) ON v.type = t.code;
--rollback DELETE FROM referentiel_type WHERE code IN ('portee-api', 'environnement-application', 'statut-application');
