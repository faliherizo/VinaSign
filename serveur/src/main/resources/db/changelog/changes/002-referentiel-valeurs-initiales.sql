--liquibase formatted sql

--changeset vinasign:002-1-referentiels-initiaux
--comment: Valeurs initiales, désignées par leur code ; les identifiants sont générés. Toute valeur s'ajoute ou se retire en base, sans redéploiement.
INSERT INTO referentiel_type (code, libelles) VALUES
  ('langue',                  '{"fr":"Langue","en":"Language"}'),
  ('niveau-authentification', '{"fr":"Niveau d''authentification","en":"Authentication level"}'),
  ('mode-enveloppe',          '{"fr":"Mode de l''enveloppe","en":"Envelope mode"}'),
  ('type-champ',              '{"fr":"Type de champ","en":"Field type"}'),
  ('canal-code',              '{"fr":"Canal du code de vérification","en":"Verification code channel"}'),
  ('motif-refus',             '{"fr":"Motif de refus","en":"Reason for declining"}'),
  ('nature-document',         '{"fr":"Nature de la version du document","en":"Document version kind"}'),
  ('ordre-signature',         '{"fr":"Ordre de signature","en":"Signing order"}'),
  ('statut-enveloppe',        '{"fr":"Statut de l''enveloppe","en":"Envelope status"}'),
  ('statut-signataire',       '{"fr":"Statut du signataire","en":"Signer status"}');

INSERT INTO referentiel_valeur (type_id, code, libelles, ordre, attributs)
SELECT t.id, v.code, v.libelles::jsonb, v.ordre, v.attributs::jsonb
FROM referentiel_type t
JOIN (VALUES
  ('langue', 'fr', '{"fr":"Français","en":"French"}', 1, '{}'),
  ('langue', 'en', '{"fr":"Anglais","en":"English"}', 2, '{}'),
  ('niveau-authentification', 'simple',   '{"fr":"Simple — lien et consentement","en":"Simple — link and consent"}', 1, '{"rang":"1"}'),
  ('niveau-authentification', 'renforce', '{"fr":"Renforcé — seconde preuve","en":"Enhanced — second factor"}', 2, '{"rang":"2"}'),
  ('niveau-authentification', 'avance',   '{"fr":"Avancé — identité vérifiée et certificat","en":"Advanced — verified identity and certificate"}', 3, '{"rang":"3"}'),
  ('mode-enveloppe', 'document',  '{"fr":"Document","en":"Document"}', 1, '{}'),
  ('mode-enveloppe', 'empreinte', '{"fr":"Empreinte (le document reste chez l''application)","en":"Digest (document stays with the application)"}', 2, '{}'),
  ('type-champ', 'signature',  '{"fr":"Signature","en":"Signature"}', 1, '{"obligatoireParDefaut":"true"}'),
  ('type-champ', 'initiales',  '{"fr":"Initiales","en":"Initials"}', 2, '{}'),
  ('type-champ', 'texte',      '{"fr":"Texte","en":"Text"}', 3, '{}'),
  ('type-champ', 'date',       '{"fr":"Date","en":"Date"}', 4, '{}'),
  ('type-champ', 'case',       '{"fr":"Case à cocher","en":"Checkbox"}', 5, '{}'),
  ('canal-code', 'courriel', '{"fr":"Courriel","en":"Email"}', 1, '{}'),
  ('canal-code', 'sms',      '{"fr":"SMS","en":"Text message"}', 2, '{}'),
  ('motif-refus', 'contenu-incorrect',  '{"fr":"Le contenu est incorrect","en":"The content is incorrect"}', 1, '{}'),
  ('motif-refus', 'mauvais-signataire', '{"fr":"Je ne suis pas la bonne personne","en":"I am not the right person"}', 2, '{}'),
  ('motif-refus', 'autre',              '{"fr":"Autre motif","en":"Other reason"}', 3, '{"precisionRequise":"true"}'),
  ('nature-document', 'original', '{"fr":"Original","en":"Original"}', 1, '{}'),
  ('nature-document', 'prepare',  '{"fr":"Préparé","en":"Prepared"}', 2, '{}'),
  ('nature-document', 'final',    '{"fr":"Final signé","en":"Signed final"}', 3, '{}'),
  ('ordre-signature', 'sequentiel', '{"fr":"Séquentiel","en":"Sequential"}', 1, '{}'),
  ('ordre-signature', 'parallele',  '{"fr":"Parallèle","en":"Parallel"}', 2, '{}'),
  ('statut-enveloppe', 'brouillon', '{"fr":"Brouillon","en":"Draft"}', 1, '{}'),
  ('statut-enveloppe', 'envoyee',   '{"fr":"Envoyée","en":"Sent"}', 2, '{}'),
  ('statut-enveloppe', 'en-cours',  '{"fr":"En cours","en":"In progress"}', 3, '{}'),
  ('statut-enveloppe', 'terminee',  '{"fr":"Terminée","en":"Completed"}', 4, '{}'),
  ('statut-enveloppe', 'refusee',   '{"fr":"Refusée","en":"Declined"}', 5, '{}'),
  ('statut-enveloppe', 'expiree',   '{"fr":"Expirée","en":"Expired"}', 6, '{}'),
  ('statut-enveloppe', 'annulee',   '{"fr":"Annulée","en":"Cancelled"}', 7, '{}'),
  ('statut-signataire', 'en-attente', '{"fr":"En attente","en":"Pending"}', 1, '{}'),
  ('statut-signataire', 'invite',     '{"fr":"Invité","en":"Invited"}', 2, '{}'),
  ('statut-signataire', 'consulte',   '{"fr":"A consulté","en":"Viewed"}', 3, '{}'),
  ('statut-signataire', 'signe',      '{"fr":"A signé","en":"Signed"}', 4, '{}'),
  ('statut-signataire', 'refuse',     '{"fr":"A refusé","en":"Declined"}', 5, '{}')
) AS v(type, code, libelles, ordre, attributs) ON v.type = t.code;
--rollback DELETE FROM referentiel_valeur; DELETE FROM referentiel_type;
