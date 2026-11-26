--liquibase formatted sql

--changeset vinasign:029-marque splitStatements:false
--comment: VS-123 — marque d'une organisation (nom affiché, couleur principale, logo, sous-domaine), lue par l'interface du signataire.
CREATE TABLE marque_organisation (
  tenant_id        UUID PRIMARY KEY REFERENCES organisation (id),
  nom_affiche      VARCHAR(80) NOT NULL,
  couleur_primaire CHAR(7) NOT NULL,
  logo             BYTEA,
  sous_domaine     VARCHAR(40) CONSTRAINT uk_marque_sous_domaine UNIQUE,
  modifie_le       TIMESTAMPTZ NOT NULL
);
ALTER TABLE marque_organisation ENABLE ROW LEVEL SECURITY;
CREATE POLICY organisation_acces ON marque_organisation TO "${roleApplicatif}"
  USING (tenant_id = NULLIF(current_setting('app.organisation', true), '')::uuid)
  WITH CHECK (tenant_id = NULLIF(current_setting('app.organisation', true), '')::uuid);
GRANT SELECT, INSERT, UPDATE ON marque_organisation TO "${roleApplicatif}";
INSERT INTO referentiel_valeur (type_id, code, libelles, ordre, attributs)
SELECT id, 'marque-gerer', '{"fr":"Gérer la marque de l''organisation","en":"Manage the organization brand"}'::jsonb, 11, '{}'::jsonb
FROM referentiel_type WHERE code = 'portee-api';
--rollback DELETE FROM referentiel_valeur WHERE code = 'marque-gerer'; DROP TABLE marque_organisation;
