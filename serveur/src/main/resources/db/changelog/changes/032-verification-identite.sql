--liquibase formatted sql

--changeset vinasign:032-verification-identite splitStatements:false
--comment: VS-131 — vérification d'identité du signataire par un fournisseur externe (port générique) : une vérification par
--         session, retrouvée par sa référence au rappel du fournisseur ; réussie, elle élève la session au niveau
--         « identité vérifiée », inséré entre « renforcé » et « avancé » (qui ajoute le certificat personnel, VS-130).
CREATE TABLE verification_identite (
  id            UUID PRIMARY KEY,
  tenant_id     UUID NOT NULL,
  session_id    UUID NOT NULL REFERENCES session_signataire (id),
  signataire_id UUID NOT NULL REFERENCES signataire (id),
  fournisseur   VARCHAR(40) NOT NULL,
  reference     VARCHAR(200) NOT NULL CONSTRAINT uk_verification_reference UNIQUE,
  etat          VARCHAR(20) NOT NULL,
  methode       VARCHAR(60),
  assurance     VARCHAR(60),
  debut         TIMESTAMPTZ NOT NULL,
  fin           TIMESTAMPTZ
);
CREATE INDEX ix_verification_signataire ON verification_identite (signataire_id, debut DESC);
ALTER TABLE verification_identite ENABLE ROW LEVEL SECURITY;
CREATE POLICY organisation_acces ON verification_identite TO "${roleApplicatif}"
  USING (tenant_id = NULLIF(current_setting('app.organisation', true), '')::uuid)
  WITH CHECK (tenant_id = NULLIF(current_setting('app.organisation', true), '')::uuid);
GRANT SELECT, INSERT ON verification_identite TO "${roleApplicatif}";
GRANT UPDATE (etat, methode, assurance, fin) ON verification_identite TO "${roleApplicatif}";
-- Le rappel du fournisseur n'appartient à aucune organisation : la référence (256 bits, inconnue hors du fournisseur) désigne
-- l'organisation, sous laquelle la suite s'exécute (exception motivée, comme l'ouverture d'un lien).
CREATE FUNCTION verification_identite_organisation(p_reference TEXT) RETURNS UUID
  LANGUAGE sql STABLE SECURITY DEFINER SET search_path = public, pg_temp AS $$
  SELECT tenant_id FROM verification_identite WHERE reference = p_reference;
$$;
REVOKE ALL ON FUNCTION verification_identite_organisation(TEXT) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION verification_identite_organisation(TEXT) TO "${roleApplicatif}";
UPDATE referentiel_valeur SET ordre = 4, attributs = attributs || '{"rang":"4"}'::jsonb
WHERE code = 'avance' AND type_id = (SELECT id FROM referentiel_type WHERE code = 'niveau-authentification');
INSERT INTO referentiel_valeur (type_id, code, libelles, ordre, attributs)
SELECT id, 'identite-verifiee', '{"fr":"Identité vérifiée — pièce contrôlée par un fournisseur","en":"Verified identity — document checked by a provider"}'::jsonb,
       3, '{"rang":"3","methode":"verification-identite"}'::jsonb
FROM referentiel_type WHERE code = 'niveau-authentification';
--rollback DELETE FROM referentiel_valeur WHERE code = 'identite-verifiee'; UPDATE referentiel_valeur SET ordre = 3, attributs = attributs || '{"rang":"3"}'::jsonb WHERE code = 'avance'; DROP FUNCTION verification_identite_organisation(TEXT); DROP TABLE verification_identite;
