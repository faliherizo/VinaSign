--liquibase formatted sql

--changeset vinasign:021-attestations splitStatements:false
--comment: VS-044 — attestation de l'utilisateur de l'application, retenue avec sa session intégrée : sujet, méthodes (amr),
--         heure d'authentification et niveau accordé. Ajout seul : une attestation n'est jamais modifiée.
CREATE TABLE attestation_lien (
  lien_id        UUID PRIMARY KEY REFERENCES lien_signature (id),
  tenant_id      UUID NOT NULL,
  sujet          VARCHAR(200) NOT NULL,
  methodes       TEXT[] NOT NULL,
  authentifie_le TIMESTAMPTZ,
  niveau         VARCHAR(30) NOT NULL,
  atteste_le     TIMESTAMPTZ NOT NULL
);
ALTER TABLE attestation_lien ENABLE ROW LEVEL SECURITY;
CREATE POLICY organisation_acces ON attestation_lien TO "${roleApplicatif}"
  USING (tenant_id = NULLIF(current_setting('app.organisation', true), '')::uuid)
  WITH CHECK (tenant_id = NULLIF(current_setting('app.organisation', true), '')::uuid);
GRANT SELECT, INSERT ON attestation_lien TO "${roleApplicatif}";
--rollback DROP TABLE attestation_lien;

--changeset vinasign:021-attestations-type-signataire
--comment: Un utilisateur de l'application est attesté par elle pour ouvrir sa session intégrée (attribut lu, aucun type figé).
UPDATE referentiel_valeur v SET attributs = v.attributs || '{"exigeAttestation":"true"}'::jsonb
FROM referentiel_type t WHERE v.type_id = t.id AND t.code = 'type-signataire' AND v.code = 'utilisateur-application' AND v.tenant_id IS NULL;
