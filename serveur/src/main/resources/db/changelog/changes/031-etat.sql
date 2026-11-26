--liquibase formatted sql

--changeset vinasign:031-etat splitStatements:false
--comment: VS-124 — page d'état : incidents de la plateforme (publics, sans organisation : hors RLS) et communications en ajout
--         seul ; référentiels de l'impact (ordre = gravité, le premier est l'état normal), des composants, des phases (cloture)
--         et des objectifs de service publiés (cible et unité en attributs).
CREATE TABLE incident (
  id         UUID PRIMARY KEY,
  titre      JSONB NOT NULL,
  impact     VARCHAR(30) NOT NULL,
  composants TEXT[] NOT NULL,
  debut      TIMESTAMPTZ NOT NULL,
  fin        TIMESTAMPTZ,
  CONSTRAINT ck_incident_periode CHECK (fin IS NULL OR fin >= debut)
);
CREATE INDEX ix_incident_fin ON incident (fin);
CREATE TABLE incident_mise_a_jour (
  id          UUID PRIMARY KEY,
  incident_id UUID NOT NULL REFERENCES incident (id),
  phase       VARCHAR(30) NOT NULL,
  message     JSONB NOT NULL,
  le          TIMESTAMPTZ NOT NULL
);
CREATE INDEX ix_incident_mise_a_jour ON incident_mise_a_jour (incident_id, le);
GRANT SELECT, INSERT ON incident, incident_mise_a_jour TO "${roleApplicatif}";
GRANT UPDATE (fin) ON incident TO "${roleApplicatif}";
INSERT INTO referentiel_type (code, libelles) VALUES
  ('impact-incident',   '{"fr":"Impact d''un incident","en":"Incident impact"}'),
  ('composant-service', '{"fr":"Composant du service","en":"Service component"}'),
  ('phase-incident',   '{"fr":"Phase d''un incident","en":"Incident phase"}'),
  ('objectif-service',  '{"fr":"Objectif de niveau de service","en":"Service level objective"}');
INSERT INTO referentiel_valeur (type_id, code, libelles, ordre, attributs)
SELECT t.id, v.code, v.libelles::jsonb, v.ordre, v.attributs::jsonb FROM referentiel_type t JOIN (VALUES
  ('impact-incident', 'operationnel', '{"fr":"Opérationnel","en":"Operational"}', 1, '{}'),
  ('impact-incident', 'degrade',      '{"fr":"Performances dégradées","en":"Degraded performance"}', 2, '{}'),
  ('impact-incident', 'partiel',      '{"fr":"Panne partielle","en":"Partial outage"}', 3, '{}'),
  ('impact-incident', 'majeur',       '{"fr":"Panne majeure","en":"Major outage"}', 4, '{}'),
  ('composant-service', 'api',           '{"fr":"API et SDK","en":"API and SDKs"}', 1, '{}'),
  ('composant-service', 'signature',     '{"fr":"Parcours de signature","en":"Signing experience"}', 2, '{}'),
  ('composant-service', 'portail',       '{"fr":"Portail d''administration","en":"Administration portal"}', 3, '{}'),
  ('composant-service', 'notifications', '{"fr":"Courriels, SMS et webhooks","en":"Emails, SMS and webhooks"}', 4, '{}'),
  ('phase-incident', 'enquete',      '{"fr":"Enquête en cours","en":"Investigating"}', 1, '{}'),
  ('phase-incident', 'identifie',    '{"fr":"Cause identifiée","en":"Identified"}', 2, '{}'),
  ('phase-incident', 'surveillance', '{"fr":"Correctif appliqué, sous surveillance","en":"Monitoring"}', 3, '{}'),
  ('phase-incident', 'resolu',       '{"fr":"Résolu","en":"Resolved"}', 4, '{"cloture":"true"}'),
  ('objectif-service', 'disponibilite',        '{"fr":"Disponibilité mensuelle","en":"Monthly availability"}', 1, '{"cible":"99.9","unite":"%"}'),
  ('objectif-service', 'latence-lecture-p95',  '{"fr":"Latence en lecture (95e centile)","en":"Read latency (95th percentile)"}', 2, '{"cible":"300","unite":"ms"}'),
  ('objectif-service', 'latence-ecriture-p95', '{"fr":"Latence en écriture (95e centile)","en":"Write latency (95th percentile)"}', 3, '{"cible":"1500","unite":"ms"}'),
  ('objectif-service', 'taux-erreur',          '{"fr":"Taux d''erreur","en":"Error rate"}', 4, '{"cible":"0.1","unite":"%"}')
) AS v(type, code, libelles, ordre, attributs) ON v.type = t.code;
--rollback DELETE FROM referentiel_valeur WHERE type_id IN (SELECT id FROM referentiel_type WHERE code IN ('impact-incident', 'composant-service', 'phase-incident', 'objectif-service')); DELETE FROM referentiel_type WHERE code IN ('impact-incident', 'composant-service', 'phase-incident', 'objectif-service'); DROP TABLE incident_mise_a_jour, incident;
