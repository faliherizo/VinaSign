--liquibase formatted sql

--changeset vinasign:024-certificat splitStatements:false
--comment: VS-072, VS-073 — textes du certificat de réalisation (référentiel, aucun texte dans le code) ; consultation du certificat et
--         export du dossier de preuve consignés au journal.
INSERT INTO referentiel_type (code, libelles) VALUES ('texte-certificat', '{"fr":"Texte du certificat de réalisation","en":"Completion certificate text"}');
INSERT INTO referentiel_valeur (type_id, code, libelles, ordre, attributs)
SELECT t.id, v.code, v.libelles::jsonb, v.ordre, '{}'::jsonb FROM referentiel_type t JOIN (VALUES
  ('titre',          '{"fr":"Certificat de réalisation","en":"Certificate of completion"}', 1),
  ('introduction',   '{"fr":"Ce certificat atteste du déroulement de la signature électronique de l''enveloppe ci-dessous, d''après le journal de preuve de VinaSign.","en":"This certificate attests to the electronic signing of the envelope below, according to the VinaSign evidence log."}', 2),
  ('enveloppe',      '{"fr":"Enveloppe","en":"Envelope"}', 3),
  ('reference',      '{"fr":"Référence","en":"Reference"}', 4),
  ('statut',         '{"fr":"Statut","en":"Status"}', 5),
  ('cree-le',        '{"fr":"Créée le","en":"Created on"}', 6),
  ('documents',      '{"fr":"Documents","en":"Documents"}', 7),
  ('nature',         '{"fr":"Version","en":"Version"}', 8),
  ('empreinte',      '{"fr":"Empreinte SHA-256","en":"SHA-256 digest"}', 9),
  ('signataires',    '{"fr":"Signataires","en":"Signers"}', 10),
  ('nom',            '{"fr":"Nom","en":"Name"}', 11),
  ('ordre',          '{"fr":"Ordre","en":"Order"}', 12),
  ('consentement',   '{"fr":"Consentement","en":"Consent"}', 13),
  ('niveau',         '{"fr":"Niveau d''authentification","en":"Authentication level"}', 14),
  ('signe-le',       '{"fr":"Agi le","en":"Acted on"}', 15),
  ('chronologie',    '{"fr":"Chronologie","en":"Timeline"}', 16),
  ('evenement',      '{"fr":"Événement","en":"Event"}', 17),
  ('acteur',         '{"fr":"Acteur","en":"Actor"}', 18),
  ('le',             '{"fr":"Le (UTC)","en":"On (UTC)"}', 19),
  ('integrite',      '{"fr":"Intégrité du journal","en":"Log integrity"}', 20),
  ('integre',        '{"fr":"Chaîne d''empreintes intacte","en":"Hash chain intact"}', 21),
  ('altere',         '{"fr":"ALTÉRATION DÉTECTÉE","en":"TAMPERING DETECTED"}', 22),
  ('ancrage',        '{"fr":"Ancrage horodaté","en":"Time-stamped anchor"}', 23),
  ('non-ancre',      '{"fr":"En attente du prochain ancrage horaire","en":"Awaiting the next hourly anchor"}', 24),
  ('cachet',         '{"fr":"Ce document est scellé par le cachet électronique de VinaSign (PAdES) : toute modification invalide le sceau.","en":"This document is sealed with the VinaSign electronic seal (PAdES): any change invalidates the seal."}', 25),
  ('genere-le',      '{"fr":"Généré le","en":"Generated on"}', 26),
  ('aucun',          '{"fr":"—","en":"—"}', 27),
  ('lisez-moi',      '{"fr":"Dossier de preuve VinaSign\n\n1. manifeste.json donne l''empreinte SHA-256 de chaque fichier de l''archive.\n2. manifeste.p7s est la signature CMS détachée de manifeste.json par le cachet de VinaSign :\n   openssl cms -verify -inform DER -binary -in manifeste.p7s -content manifeste.json -noverify\n   (avec le certificat de l''autorité, remplacer -noverify par -CAfile).\n3. journal.json est la chaîne d''empreintes : chaque événement porte l''empreinte du précédent ;\n   empreinte = SHA-256 de « précédente|enveloppe|rang|type|acteur|microsecondes|données ».\n4. ancrage.json et ancrage.p7s : empreinte agrégée de la fenêtre horaire qui couvre le journal, signée et horodatée.\n5. certificat.pdf est le certificat de réalisation (PDF/A-2b scellé en PAdES) ; documents/ contient les versions du document.","en":"VinaSign evidence package\n\n1. manifeste.json lists the SHA-256 digest of every file in the archive.\n2. manifeste.p7s is the detached CMS signature of manifeste.json by the VinaSign seal:\n   openssl cms -verify -inform DER -binary -in manifeste.p7s -content manifeste.json -noverify\n   (with the authority certificate, replace -noverify with -CAfile).\n3. journal.json is the hash chain: each event carries the digest of the previous one;\n   digest = SHA-256 of \"previous|envelope|rank|type|actor|microseconds|data\".\n4. ancrage.json and ancrage.p7s: aggregated digest of the hourly window covering the log, signed and time-stamped.\n5. certificat.pdf is the certificate of completion (PDF/A-2b sealed with PAdES); documents/ holds the document versions."}', 28)
) AS v(code, libelles, ordre) ON t.code = 'texte-certificat';
INSERT INTO referentiel_valeur (type_id, code, libelles, ordre, attributs)
SELECT t.id, v.code, v.libelles::jsonb, v.ordre, '{}'::jsonb FROM referentiel_type t JOIN (VALUES
  ('certificat-produit', '{"fr":"Certificat de réalisation produit","en":"Completion certificate produced"}', 21),
  ('preuve-exportee',    '{"fr":"Dossier de preuve exporté","en":"Evidence package exported"}', 22)
) AS v(code, libelles, ordre) ON t.code = 'evenement-enveloppe';
--rollback DELETE FROM referentiel_valeur WHERE code IN ('certificat-produit', 'preuve-exportee'); DELETE FROM referentiel_type WHERE code = 'texte-certificat';
