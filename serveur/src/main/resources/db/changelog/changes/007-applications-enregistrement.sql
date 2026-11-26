--liquibase formatted sql

--changeset vinasign:007-1-enregistrement-applications splitStatements:false
--comment: VS-011 — origines d'intégration et unicité des clés d'application sur toute la plateforme.
ALTER TABLE application_cliente ADD COLUMN origines_integration TEXT[] NOT NULL DEFAULT '{}';
CREATE INDEX ix_application_cliente_organisation ON application_cliente (tenant_id, nom);

-- Empreinte RFC 7638 de chaque clé publique : une clé n'appartient qu'à une application, ce qui interdit de partager
-- une clé entre organisations comme entre bac à sable et production. Table de la plateforme, sans donnée d'une
-- organisation (une empreinte et un identifiant public) : elle doit être vue entière pour que l'unicité soit vérifiée.
CREATE TABLE empreinte_cle_application (
  empreinte VARCHAR(64) PRIMARY KEY,
  client_id VARCHAR(80) NOT NULL REFERENCES application_cliente (client_id) ON DELETE CASCADE
);
GRANT SELECT, INSERT, DELETE ON empreinte_cle_application TO "${roleApplicatif}";
--rollback DROP TABLE empreinte_cle_application; DROP INDEX ix_application_cliente_organisation; ALTER TABLE application_cliente DROP COLUMN origines_integration;

--changeset vinasign:007-2-action-audit-application
INSERT INTO referentiel_valeur (type_id, code, libelles, ordre, attributs)
SELECT id, 'application-enregistree', '{"fr":"Application cliente enregistrée","en":"Client application registered"}'::jsonb, 10, '{}'::jsonb
FROM referentiel_type WHERE code = 'action-audit';
--rollback DELETE FROM referentiel_valeur WHERE code = 'application-enregistree';
