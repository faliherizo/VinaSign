--liquibase formatted sql

--changeset vinasign:004-1-rls-referentiel splitStatements:false
--comment: VS-005 — référentiels : lecture seule pour l'application ; une valeur propre à une organisation n'est visible que d'elle, une valeur commune (tenant_id nul) de tous.
GRANT SELECT ON referentiel_type, referentiel_valeur TO "${roleApplicatif}";
ALTER TABLE referentiel_valeur ENABLE ROW LEVEL SECURITY;
CREATE POLICY organisation_lecture ON referentiel_valeur FOR SELECT TO "${roleApplicatif}"
  USING (tenant_id IS NULL OR tenant_id = NULLIF(current_setting('app.organisation', true), '')::uuid);
--rollback DROP POLICY organisation_lecture ON referentiel_valeur; ALTER TABLE referentiel_valeur DISABLE ROW LEVEL SECURITY; REVOKE SELECT ON referentiel_type, referentiel_valeur FROM "${roleApplicatif}";
