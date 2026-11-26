--liquibase formatted sql

--changeset vinasign:006-1-organisation splitStatements:false
--comment: VS-010 — organisations de la plateforme et journal d'audit.
CREATE TABLE organisation (
  id         UUID PRIMARY KEY,
  nom        VARCHAR(200) NOT NULL,
  statut     VARCHAR(30)  NOT NULL,
  cree_le    TIMESTAMPTZ  NOT NULL,
  modifie_le TIMESTAMPTZ  NOT NULL
);
-- Table de la plateforme, non isolée par la RLS : elle ne contient aucune donnée d'une organisation, seulement son
-- existence et son état, que le contrôle d'activité lit à chaque requête. Son écriture passe par le cas d'utilisation
-- réservé à l'administrateur de la plateforme.
GRANT SELECT, INSERT, UPDATE ON organisation TO "${roleApplicatif}";

-- Une application cliente appartient à une organisation existante. NOT VALID : imposé à toute nouvelle ligne, sans
-- rejeter d'éventuelles lignes antérieures d'une base de poste.
ALTER TABLE application_cliente
  ADD CONSTRAINT fk_application_organisation FOREIGN KEY (tenant_id) REFERENCES organisation (id) NOT VALID;

CREATE TABLE journal_audit (
  id         UUID PRIMARY KEY,
  tenant_id  UUID NOT NULL,
  acteur     VARCHAR(200) NOT NULL,
  action     VARCHAR(80)  NOT NULL,
  cible      VARCHAR(200),
  survenu_le TIMESTAMPTZ  NOT NULL
);
CREATE INDEX ix_journal_audit_organisation ON journal_audit (tenant_id, survenu_le);
-- Ajout seul : ni UPDATE ni DELETE pour l'application. Lecture limitée à l'organisation courante ; l'écriture est
-- permise pour toute organisation, car l'administration de la plateforme agit hors de toute organisation.
GRANT SELECT, INSERT ON journal_audit TO "${roleApplicatif}";
ALTER TABLE journal_audit ENABLE ROW LEVEL SECURITY;
CREATE POLICY organisation_lecture ON journal_audit FOR SELECT TO "${roleApplicatif}"
  USING (tenant_id = NULLIF(current_setting('app.organisation', true), '')::uuid);
CREATE POLICY ajout ON journal_audit FOR INSERT TO "${roleApplicatif}" WITH CHECK (true);
--rollback DROP TABLE journal_audit; ALTER TABLE application_cliente DROP CONSTRAINT fk_application_organisation; DROP TABLE organisation;

--changeset vinasign:006-2-referentiels-organisation
--comment: Statuts d'organisation, actions d'audit et rôles du portail : des référentiels.
INSERT INTO referentiel_type (code, libelles) VALUES
  ('statut-organisation', '{"fr":"Statut de l''organisation","en":"Organization status"}'),
  ('action-audit',        '{"fr":"Action consignée","en":"Audited action"}');
INSERT INTO referentiel_valeur (type_id, code, libelles, ordre, attributs)
SELECT t.id, v.code, v.libelles::jsonb, v.ordre, '{}'::jsonb FROM referentiel_type t JOIN (VALUES
  ('statut-organisation', 'active',    '{"fr":"Active","en":"Active"}', 1),
  ('statut-organisation', 'suspendue', '{"fr":"Suspendue","en":"Suspended"}', 2),
  ('action-audit', 'organisation-creee',     '{"fr":"Organisation créée","en":"Organization created"}', 1),
  ('action-audit', 'organisation-suspendue', '{"fr":"Organisation suspendue","en":"Organization suspended"}', 2),
  ('action-audit', 'organisation-reactivee', '{"fr":"Organisation réactivée","en":"Organization reactivated"}', 3)
) AS v(type, code, libelles, ordre) ON v.type = t.code;
--rollback DELETE FROM referentiel_type WHERE code IN ('statut-organisation', 'action-audit');
