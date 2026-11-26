--liquibase formatted sql

--changeset vinasign:038-taille-type-champ
--comment: VS-060 — taille d'un champ à sa pose (fraction de la page), lue par l'interface de placement ; aucune taille figée dans le code.
UPDATE referentiel_valeur v SET attributs = v.attributs || jsonb_build_object('largeur', x.largeur, 'hauteur', x.hauteur)
FROM referentiel_type t, (VALUES ('signature', '0.28', '0.07'), ('initiales', '0.12', '0.055'), ('texte', '0.30', '0.055'), ('date', '0.20', '0.055'),
  ('case', '0.07', '0.055')) AS x(code, largeur, hauteur)
WHERE v.type_id = t.id AND t.code = 'type-champ' AND v.code = x.code AND v.tenant_id IS NULL;

--changeset vinasign:038-rendu-initiales
--comment: VS-060 — une zone d'initiales reçoit les initiales du signataire, non l'image de sa signature entière.
UPDATE referentiel_valeur v SET attributs = v.attributs || '{"rendu":"initiales"}'::jsonb
FROM referentiel_type t WHERE v.type_id = t.id AND t.code = 'type-champ' AND v.code = 'initiales' AND v.tenant_id IS NULL;
