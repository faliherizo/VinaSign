--liquibase formatted sql

--changeset vinasign:015-1-defis-code splitStatements:false
--comment: VS-042 — codes à usage unique (empreinte HMAC seulement), niveau atteint par la session.
CREATE TABLE defi_code (
  id             UUID PRIMARY KEY,
  tenant_id      UUID NOT NULL,
  session_id     UUID NOT NULL REFERENCES session_signataire (id),
  canal          VARCHAR(30) NOT NULL,
  empreinte_code CHAR(64) NOT NULL,
  expire_le      TIMESTAMPTZ NOT NULL,
  essais         INT NOT NULL DEFAULT 0,
  essais_max     INT NOT NULL,
  valide_le      TIMESTAMPTZ,
  cree_le        TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp()
);
CREATE INDEX ix_defi_session ON defi_code (session_id, cree_le);
ALTER TABLE defi_code ENABLE ROW LEVEL SECURITY;
CREATE POLICY organisation_acces ON defi_code TO "${roleApplicatif}"
  USING (tenant_id = NULLIF(current_setting('app.organisation', true), '')::uuid)
  WITH CHECK (tenant_id = NULLIF(current_setting('app.organisation', true), '')::uuid);
GRANT SELECT, INSERT, UPDATE ON defi_code TO "${roleApplicatif}";
ALTER TABLE session_signataire ADD COLUMN niveau_atteint VARCHAR(30);
GRANT UPDATE (niveau_atteint) ON session_signataire TO "${roleApplicatif}";
--rollback ALTER TABLE session_signataire DROP COLUMN niveau_atteint; DROP TABLE defi_code;

--changeset vinasign:015-2-modele-code
INSERT INTO referentiel_valeur (type_id, code, libelles, ordre, attributs)
SELECT t.id, v.code, v.libelles::jsonb, v.ordre, '{}'::jsonb FROM referentiel_type t JOIN (VALUES
  ('code-sujet', '{"fr":"Votre code de vérification","en":"Your verification code"}', 5),
  ('code-corps', '{"fr":"Bonjour {nom},\n\nVotre code de vérification est : {code}\n\nIl expire dans quelques minutes. Ne le communiquez à personne.","en":"Hello {nom},\n\nYour verification code is: {code}\n\nIt expires in a few minutes. Do not share it with anyone."}', 6)
) AS v(code, libelles, ordre) ON t.code = 'modele-courriel';
--rollback DELETE FROM referentiel_valeur WHERE code IN ('code-sujet', 'code-corps');
