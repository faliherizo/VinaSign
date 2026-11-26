--liquibase formatted sql

--changeset vinasign:010-1-plans
--comment: VS-015 — plans : débit (requêtes par minute, par demandeur) et quota d'enveloppes par mois (appliqué avec VS-020).
INSERT INTO referentiel_type (code, libelles) VALUES ('plan', '{"fr":"Plan","en":"Plan"}');
INSERT INTO referentiel_valeur (type_id, code, libelles, ordre, attributs)
SELECT t.id, v.code, v.libelles::jsonb, v.ordre, v.attributs::jsonb FROM referentiel_type t JOIN (VALUES
  ('plan', 'essentiel',     '{"fr":"Essentiel","en":"Essential"}', 1, '{"parDefaut":"true","requetesParMinute":"120","enveloppesParMois":"100"}'),
  ('plan', 'professionnel', '{"fr":"Professionnel","en":"Professional"}', 2, '{"requetesParMinute":"600","enveloppesParMois":"1000"}'),
  ('plan', 'entreprise',    '{"fr":"Entreprise","en":"Enterprise"}', 3, '{"requetesParMinute":"3000","enveloppesParMois":"10000"}'),
  ('action-audit', 'plan-organisation-change', '{"fr":"Plan de l''organisation changé","en":"Organization plan changed"}', 4, '{}')
) AS v(type, code, libelles, ordre, attributs) ON v.type = t.code;
-- Les organisations existantes reçoivent le plan par défaut ; toute nouvelle organisation reçoit le sien du cas d'utilisation.
ALTER TABLE organisation ADD COLUMN plan VARCHAR(60);
UPDATE organisation SET plan = (SELECT v.code FROM referentiel_valeur v JOIN referentiel_type t ON t.id = v.type_id
                                WHERE t.code = 'plan' AND v.attributs ->> 'parDefaut' = 'true' AND v.tenant_id IS NULL LIMIT 1);
ALTER TABLE organisation ALTER COLUMN plan SET NOT NULL;
--rollback ALTER TABLE organisation DROP COLUMN plan; DELETE FROM referentiel_valeur WHERE code = 'plan-organisation-change'; DELETE FROM referentiel_type WHERE code = 'plan';
