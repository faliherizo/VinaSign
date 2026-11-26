--liquibase formatted sql

--changeset vinasign:037-1-liste-enveloppes
--comment: VS-064 — liste paginée des enveloppes d'une application ou de l'espace portail, les plus récentes d'abord (pagination par clé).
CREATE INDEX ix_enveloppe_liste ON enveloppe (tenant_id, client_id, cree_le DESC, id DESC);
--rollback DROP INDEX ix_enveloppe_liste;

--changeset vinasign:037-2-mode-depose-document
--comment: VS-064 — le portail n'offre que les modes où le document est déposé chez VinaSign (il n'a pas d'application pour garder le document).
UPDATE referentiel_valeur SET attributs = attributs || '{"deposeDocument":"true"}'::jsonb
WHERE code = 'document' AND type_id = (SELECT id FROM referentiel_type WHERE code = 'mode-enveloppe');
--rollback UPDATE referentiel_valeur SET attributs = attributs - 'deposeDocument' WHERE code = 'document' AND type_id = (SELECT id FROM referentiel_type WHERE code = 'mode-enveloppe');
