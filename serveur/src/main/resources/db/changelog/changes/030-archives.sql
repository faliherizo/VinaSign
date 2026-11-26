--liquibase formatted sql

--changeset vinasign:030-archives splitStatements:false
--comment: VS-132 — validité à long terme : chaque état archivé du document final (le final lui-même, puis chaque renouvellement
--         par un nouvel horodatage de document) et l'échéance de son dernier horodatage (fin de validité du certificat de
--         l'autorité). En ajout seul, comme les versions. Échéance nulle : document sans horodatage, rien à renouveler.
CREATE TABLE archive_document (
  id           UUID PRIMARY KEY,
  tenant_id    UUID NOT NULL,
  enveloppe_id UUID NOT NULL REFERENCES enveloppe (id),
  cle_objet    VARCHAR(400) NOT NULL,
  sha256       CHAR(64) NOT NULL,
  taille       BIGINT NOT NULL,
  echeance     TIMESTAMPTZ,
  cree_le      TIMESTAMPTZ NOT NULL
);
CREATE INDEX ix_archive_enveloppe ON archive_document (enveloppe_id, cree_le DESC);
ALTER TABLE archive_document ENABLE ROW LEVEL SECURITY;
CREATE POLICY organisation_acces ON archive_document TO "${roleApplicatif}"
  USING (tenant_id = NULLIF(current_setting('app.organisation', true), '')::uuid)
  WITH CHECK (tenant_id = NULLIF(current_setting('app.organisation', true), '')::uuid);
GRANT SELECT, INSERT ON archive_document TO "${roleApplicatif}";
-- Tâche de plateforme, toutes organisations (exception motivée, comme la finalisation) : enveloppes terminées dont le
-- dernier état archivé arrive à échéance avant p_limite, ou dont le document final n'a pas encore été évalué.
CREATE FUNCTION archives_a_renouveler(p_limite TIMESTAMPTZ, p_lot INT) RETURNS TABLE (id UUID, tenant_id UUID)
  LANGUAGE sql STABLE SECURITY DEFINER SET search_path = public, pg_temp AS $$
  SELECT e.id, e.tenant_id FROM enveloppe e
  LEFT JOIN LATERAL (SELECT a.echeance FROM archive_document a WHERE a.enveloppe_id = e.id ORDER BY a.cree_le DESC LIMIT 1) d ON true
  WHERE e.statut = 'terminee'
    AND EXISTS (SELECT 1 FROM version_document v WHERE v.enveloppe_id = e.id AND v.nature = 'final')
    AND (NOT EXISTS (SELECT 1 FROM archive_document a WHERE a.enveloppe_id = e.id) OR d.echeance < p_limite)
  ORDER BY d.echeance NULLS FIRST, e.modifie_le LIMIT p_lot;
$$;
REVOKE ALL ON FUNCTION archives_a_renouveler(TIMESTAMPTZ, INT) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION archives_a_renouveler(TIMESTAMPTZ, INT) TO "${roleApplicatif}";
INSERT INTO referentiel_valeur (type_id, code, libelles, ordre, attributs)
SELECT t.id, 'archive-renouvelee', '{"fr":"Horodatage d''archive renouvelé","en":"Archive timestamp renewed"}'::jsonb, 30, '{}'::jsonb
FROM referentiel_type t WHERE t.code = 'evenement-enveloppe';
--rollback DELETE FROM referentiel_valeur WHERE code = 'archive-renouvelee'; DROP FUNCTION archives_a_renouveler(TIMESTAMPTZ, INT); DROP TABLE archive_document;
