--liquibase formatted sql

--changeset vinasign:027-cles-acces splitStatements:false
--comment: VS-043 — clés d'accès (WebAuthn) des signataires récurrents : rattachées à une identité (empreinte de l'organisation et du
--         sujet externe ou du courriel), jamais à une enveloppe ; défis à usage unique liés à la session ; méthode « hwk » retenue.
CREATE TABLE cle_acces (
  id             UUID PRIMARY KEY,
  tenant_id      UUID NOT NULL,
  identite       CHAR(64) NOT NULL,
  credential_id  VARCHAR(400) NOT NULL CONSTRAINT uk_cle_acces_credential UNIQUE,
  donnees        BYTEA NOT NULL,
  compteur       BIGINT NOT NULL,
  cree_le        TIMESTAMPTZ NOT NULL,
  utilisee_le    TIMESTAMPTZ
);
CREATE INDEX ix_cle_acces_identite ON cle_acces (tenant_id, identite);
CREATE TABLE defi_cle_acces (
  session_id  UUID PRIMARY KEY REFERENCES session_signataire (id),
  tenant_id   UUID NOT NULL,
  nature      VARCHAR(20) NOT NULL,
  defi        BYTEA NOT NULL,
  expire_le   TIMESTAMPTZ NOT NULL
);
DO $$
DECLARE t TEXT;
BEGIN
  FOREACH t IN ARRAY ARRAY['cle_acces', 'defi_cle_acces'] LOOP
    EXECUTE format('ALTER TABLE %I ENABLE ROW LEVEL SECURITY', t);
    EXECUTE format('CREATE POLICY organisation_acces ON %I TO "${roleApplicatif}" '
                   'USING (tenant_id = NULLIF(current_setting(''app.organisation'', true), '''')::uuid) '
                   'WITH CHECK (tenant_id = NULLIF(current_setting(''app.organisation'', true), '''')::uuid)', t);
  END LOOP;
END $$;
GRANT SELECT, INSERT, UPDATE ON cle_acces TO "${roleApplicatif}";
GRANT SELECT, INSERT, UPDATE, DELETE ON defi_cle_acces TO "${roleApplicatif}";
ALTER TABLE session_signataire ADD COLUMN amr VARCHAR(20);
GRANT UPDATE (amr) ON session_signataire TO "${roleApplicatif}";
--rollback ALTER TABLE session_signataire DROP COLUMN amr; DROP TABLE defi_cle_acces; DROP TABLE cle_acces;
