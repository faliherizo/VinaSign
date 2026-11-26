--liquibase formatted sql

--changeset vinasign:033-inscriptions splitStatements:false
--comment: VS-122 — inscription en libre-service. Table de la plateforme, comme organisation (aucune organisation n'existe
--         encore) : nom demandé, courriel du demandeur, domaine à prouver, empreinte du jeton de confirmation (jamais le
--         jeton), défi DNS. Au bac à sable, le courriel confirmé suffit ; en production, domaine prouvé et validation.
CREATE TABLE inscription (
  id               UUID PRIMARY KEY,
  nom_organisation VARCHAR(200) NOT NULL,
  courriel         VARCHAR(320) NOT NULL,
  domaine          VARCHAR(253),
  langue           VARCHAR(10) NOT NULL,
  etat             VARCHAR(30) NOT NULL,
  empreinte_jeton  CHAR(64) NOT NULL CONSTRAINT uk_inscription_jeton UNIQUE,
  jeton_expire_le  TIMESTAMPTZ NOT NULL,
  defi_dns         VARCHAR(100) NOT NULL,
  motif_refus      VARCHAR(500),
  cree_le          TIMESTAMPTZ NOT NULL,
  modifie_le       TIMESTAMPTZ NOT NULL
);
-- Une seule demande ouverte par courriel : une seconde demande ne multiplie pas les courriels envoyés.
CREATE UNIQUE INDEX uk_inscription_ouverte ON inscription (lower(courriel)) WHERE etat NOT IN ('acceptee', 'refusee');
GRANT SELECT, INSERT, UPDATE ON inscription TO "${roleApplicatif}";
INSERT INTO referentiel_valeur (type_id, code, libelles, ordre)
SELECT t.id, v.code, v.libelles::jsonb, v.ordre FROM referentiel_type t JOIN (VALUES
  ('inscription-sujet', '{"fr":"Confirmez la création de {organisation} sur VinaSign","en":"Confirm the creation of {organisation} on VinaSign"}', 40),
  ('inscription-corps', '{"fr":"Bonjour,\n\nPour confirmer votre adresse et poursuivre la création de {organisation}, ouvrez ce lien dans les 24 heures :\n{lien}\n\nSi vous n''êtes pas à l''origine de cette demande, ignorez ce message.","en":"Hello,\n\nTo confirm your address and continue creating {organisation}, open this link within 24 hours:\n{lien}\n\nIf you did not make this request, ignore this message."}', 41)
) AS v(code, libelles, ordre) ON t.code = 'modele-courriel';
--rollback DELETE FROM referentiel_valeur WHERE code IN ('inscription-sujet', 'inscription-corps'); DROP TABLE inscription;
