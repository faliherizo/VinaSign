--liquibase formatted sql

--changeset vinasign:012-1-liens-sessions-consentements splitStatements:false
--comment: VS-040, VS-041 — liens des signataires (empreinte du jeton seulement), sessions, consentements. Isolés par organisation.
CREATE TABLE lien_signature (
  id               UUID PRIMARY KEY,
  tenant_id        UUID NOT NULL,
  enveloppe_id     UUID NOT NULL REFERENCES enveloppe (id),
  signataire_id    UUID NOT NULL REFERENCES signataire (id),
  empreinte_jeton  CHAR(64) NOT NULL CONSTRAINT uk_lien_jeton UNIQUE,
  expire_le        TIMESTAMPTZ NOT NULL,
  utilisations     INT NOT NULL DEFAULT 0,
  utilisations_max INT NOT NULL,
  revoque_le       TIMESTAMPTZ,
  consomme_le      TIMESTAMPTZ
);
CREATE INDEX ix_lien_signataire ON lien_signature (signataire_id);

CREATE TABLE session_signataire (
  id              UUID PRIMARY KEY,
  tenant_id       UUID NOT NULL,
  enveloppe_id    UUID NOT NULL REFERENCES enveloppe (id),
  signataire_id   UUID NOT NULL REFERENCES signataire (id),
  empreinte_jeton CHAR(64) NOT NULL CONSTRAINT uk_session_jeton UNIQUE,
  expire_le       TIMESTAMPTZ NOT NULL
);

CREATE TABLE consentement (
  id              UUID PRIMARY KEY,
  tenant_id       UUID NOT NULL,
  enveloppe_id    UUID NOT NULL REFERENCES enveloppe (id),
  signataire_id   UUID NOT NULL REFERENCES signataire (id),
  version_texte   VARCHAR(60) NOT NULL,
  empreinte_texte CHAR(64) NOT NULL,
  accepte_le      TIMESTAMPTZ NOT NULL
);
CREATE INDEX ix_consentement_signataire ON consentement (signataire_id, accepte_le);

DO $$
DECLARE t TEXT;
BEGIN
  FOREACH t IN ARRAY ARRAY['lien_signature', 'session_signataire', 'consentement'] LOOP
    EXECUTE format('ALTER TABLE %I ENABLE ROW LEVEL SECURITY', t);
    EXECUTE format('CREATE POLICY organisation_acces ON %I TO "${roleApplicatif}" '
                   'USING (tenant_id = NULLIF(current_setting(''app.organisation'', true), '''')::uuid) '
                   'WITH CHECK (tenant_id = NULLIF(current_setting(''app.organisation'', true), '''')::uuid)', t);
  END LOOP;
END $$;
GRANT SELECT, INSERT, UPDATE ON lien_signature TO "${roleApplicatif}";
GRANT SELECT, INSERT ON session_signataire, consentement TO "${roleApplicatif}";

-- Exceptions motivées : le signataire n'est authentifié que par son jeton ; l'organisation n'est connue qu'une fois le
-- lien ou la session retrouvé. Les fonctions ne rendent que la ligne de ce jeton.
CREATE FUNCTION session_lien_par_jeton(p_empreinte TEXT) RETURNS SETOF lien_signature
  LANGUAGE sql STABLE SECURITY DEFINER SET search_path = public, pg_temp AS 'SELECT * FROM lien_signature WHERE empreinte_jeton = p_empreinte';
CREATE FUNCTION session_par_jeton(p_empreinte TEXT) RETURNS SETOF session_signataire
  LANGUAGE sql STABLE SECURITY DEFINER SET search_path = public, pg_temp AS 'SELECT * FROM session_signataire WHERE empreinte_jeton = p_empreinte';
REVOKE ALL ON FUNCTION session_lien_par_jeton(TEXT), session_par_jeton(TEXT) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION session_lien_par_jeton(TEXT), session_par_jeton(TEXT) TO "${roleApplicatif}";
--rollback DROP FUNCTION session_par_jeton(TEXT); DROP FUNCTION session_lien_par_jeton(TEXT); DROP TABLE consentement, session_signataire, lien_signature;

--changeset vinasign:012-2-texte-consentement
--comment: Texte du consentement versionné (VS-041) : une nouvelle version s'ajoute en base. Libellé = texte affiché.
INSERT INTO referentiel_type (code, libelles) VALUES ('texte-consentement', '{"fr":"Consentement à signer électroniquement","en":"Consent to sign electronically"}');
INSERT INTO referentiel_valeur (type_id, code, libelles, ordre, attributs)
SELECT id, 'consentement-2026-1', '{"fr":"J''accepte de signer ce document par voie électronique. Ma signature électronique a la même valeur que ma signature manuscrite. Je peux demander une copie papier à l''expéditeur.","en":"I agree to sign this document electronically. My electronic signature has the same effect as my handwritten signature. I may ask the sender for a paper copy."}'::jsonb,
       1, '{"enVigueur":"true"}'::jsonb FROM referentiel_type WHERE code = 'texte-consentement';
--rollback DELETE FROM referentiel_type WHERE code = 'texte-consentement';
