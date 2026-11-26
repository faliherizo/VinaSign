--liquibase formatted sql

--changeset vinasign:020-saisie-type-champ
--comment: VS-052 — contrôle de saisie de chaque type de champ, lu par l'interface du signataire (aucun type figé dans le code).
--         « signature » : rempli par l'acte de signer ; « texte », « date », « case » : saisis par le signataire.
UPDATE referentiel_valeur v SET attributs = v.attributs || jsonb_build_object('saisie', x.saisie)
FROM referentiel_type t, (VALUES ('signature', 'signature'), ('initiales', 'signature'), ('texte', 'texte'), ('date', 'date'), ('case', 'case')) AS x(code, saisie)
WHERE v.type_id = t.id AND t.code = 'type-champ' AND v.code = x.code AND v.tenant_id IS NULL;
