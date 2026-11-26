--liquibase formatted sql

--changeset vinasign:019-1-champs-signatures splitStatements:false
--comment: VS-051, VS-052, VS-045 — champs (coordonnées normalisées), valeurs saisies, trace des signatures (image ou nom tapé, sans donnée de tracé).
CREATE TABLE champ (
  id            UUID PRIMARY KEY,
  tenant_id     UUID NOT NULL,
  enveloppe_id  UUID NOT NULL REFERENCES enveloppe (id),
  signataire_id UUID NOT NULL REFERENCES signataire (id),
  type          VARCHAR(30) NOT NULL,
  libelle       VARCHAR(120) NOT NULL,
  page          INT NOT NULL,
  x DOUBLE PRECISION NOT NULL, y DOUBLE PRECISION NOT NULL, largeur DOUBLE PRECISION NOT NULL, hauteur DOUBLE PRECISION NOT NULL,
  obligatoire   BOOLEAN NOT NULL
);
CREATE TABLE valeur_champ (
  tenant_id     UUID NOT NULL,
  champ_id      UUID NOT NULL REFERENCES champ (id),
  signataire_id UUID NOT NULL,
  valeur        TEXT NOT NULL,
  saisi_le      TIMESTAMPTZ NOT NULL DEFAULT now(),
  PRIMARY KEY (champ_id)
);
CREATE TABLE signature_signataire (
  id            UUID PRIMARY KEY,
  tenant_id     UUID NOT NULL,
  enveloppe_id  UUID NOT NULL REFERENCES enveloppe (id),
  signataire_id UUID NOT NULL REFERENCES signataire (id) CONSTRAINT uk_signature_signataire UNIQUE,
  mode          VARCHAR(20) NOT NULL,
  cle_image     VARCHAR(400),
  nom_saisi     VARCHAR(200),
  signe_le      TIMESTAMPTZ NOT NULL
);
DO $$
DECLARE t TEXT;
BEGIN
  FOREACH t IN ARRAY ARRAY['champ', 'valeur_champ', 'signature_signataire'] LOOP
    EXECUTE format('ALTER TABLE %I ENABLE ROW LEVEL SECURITY', t);
    EXECUTE format('CREATE POLICY organisation_acces ON %I TO "${roleApplicatif}" '
                   'USING (tenant_id = NULLIF(current_setting(''app.organisation'', true), '''')::uuid) '
                   'WITH CHECK (tenant_id = NULLIF(current_setting(''app.organisation'', true), '''')::uuid)', t);
  END LOOP;
END $$;
GRANT SELECT, INSERT, DELETE ON champ TO "${roleApplicatif}";
GRANT SELECT, INSERT ON valeur_champ, signature_signataire TO "${roleApplicatif}";
--rollback DROP TABLE signature_signataire, valeur_champ, champ;

--changeset vinasign:019-2-evenements-signataire
INSERT INTO referentiel_valeur (type_id, code, libelles, ordre, attributs)
SELECT t.id, v.code, v.libelles::jsonb, v.ordre, v.attributs::jsonb FROM referentiel_type t JOIN (VALUES
  ('champs-definis',         '{"fr":"Champs définis","en":"Fields set"}', 7, '{}'),
  ('signataire-a-signe',     '{"fr":"Signataire a signé","en":"Signer signed"}', 8, '{"webhook":"signer.signed"}'),
  ('signataire-a-refuse',    '{"fr":"Signataire a refusé","en":"Signer declined"}', 9, '{"webhook":"signer.declined"}'),
  ('signatures-recueillies', '{"fr":"Toutes les signatures recueillies","en":"All signatures collected"}', 10, '{}')
) AS v(code, libelles, ordre, attributs) ON t.code = 'evenement-enveloppe';
--rollback DELETE FROM referentiel_valeur WHERE code IN ('champs-definis', 'signataire-a-signe', 'signataire-a-refuse', 'signatures-recueillies');
