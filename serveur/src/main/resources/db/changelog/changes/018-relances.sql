--liquibase formatted sql

--changeset vinasign:018-1-relances splitStatements:false
--comment: VS-082 — rang de relance et émission des liens ; candidats aux relances ; aucun courriel envoyé pour une enveloppe terminée.
ALTER TABLE lien_signature ADD COLUMN emis_le TIMESTAMPTZ NOT NULL DEFAULT now();
ALTER TABLE lien_signature ADD COLUMN rang_relance INT NOT NULL DEFAULT 0;
ALTER TABLE courriel_sortant ADD COLUMN enveloppe_id UUID;

-- Exception motivée : la tâche de relance parcourt toutes les organisations ; elle n'agit ensuite que sous la RLS de chacune.
CREATE FUNCTION relances_liens_actifs(p_maintenant TIMESTAMPTZ, p_limite INT) RETURNS SETOF lien_signature
  LANGUAGE sql STABLE SECURITY DEFINER SET search_path = public, pg_temp AS $$
  SELECT l.* FROM lien_signature l JOIN enveloppe e ON e.id = l.enveloppe_id
  WHERE e.statut = 'envoyee' AND l.revoque_le IS NULL AND l.consomme_le IS NULL AND l.expire_le > p_maintenant AND l.utilisations_max > 1
  ORDER BY l.emis_le LIMIT p_limite;
$$;
REVOKE ALL ON FUNCTION relances_liens_actifs(TIMESTAMPTZ, INT) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION relances_liens_actifs(TIMESTAMPTZ, INT) TO "${roleApplicatif}";

-- L'expédition abandonne d'abord les courriels d'une enveloppe terminée (annulée, expirée, refusée, signée) : aucune
-- invitation ni relance ne part après une annulation, même déjà déposée.
CREATE OR REPLACE FUNCTION courriel_dus(p_maintenant TIMESTAMPTZ, p_limite INT) RETURNS SETOF courriel_sortant
  LANGUAGE plpgsql VOLATILE SECURITY DEFINER SET search_path = public, pg_temp AS $$
BEGIN
  UPDATE courriel_sortant c SET abandonne_le = p_maintenant, corps = NULL
  FROM enveloppe e WHERE c.enveloppe_id = e.id AND e.statut IN ('annulee', 'expiree', 'refusee', 'terminee')
    AND c.envoye_le IS NULL AND c.abandonne_le IS NULL;
  RETURN QUERY UPDATE courriel_sortant c SET prochaine_tentative = p_maintenant + interval '5 minutes'
  WHERE c.id IN (SELECT id FROM courriel_sortant WHERE envoye_le IS NULL AND abandonne_le IS NULL AND prochaine_tentative <= p_maintenant
                 ORDER BY prochaine_tentative LIMIT p_limite FOR UPDATE SKIP LOCKED)
  RETURNING c.*;
END $$;
--rollback DROP FUNCTION relances_liens_actifs(TIMESTAMPTZ, INT); ALTER TABLE courriel_sortant DROP COLUMN enveloppe_id; ALTER TABLE lien_signature DROP COLUMN rang_relance; ALTER TABLE lien_signature DROP COLUMN emis_le;

--changeset vinasign:018-2-modele-relance
INSERT INTO referentiel_valeur (type_id, code, libelles, ordre, attributs)
SELECT t.id, v.code, v.libelles::jsonb, v.ordre, '{}'::jsonb FROM referentiel_type t JOIN (VALUES
  ('relance-sujet', '{"fr":"Rappel : document à signer ({reference})","en":"Reminder: document to sign ({reference})"}', 9),
  ('relance-corps', '{"fr":"Bonjour {nom},\n\nUn document attend toujours votre signature. Votre nouveau lien personnel (l''ancien ne fonctionne plus) :\n{lien}\n\nIl expire le {echeance}.","en":"Hello {nom},\n\nA document is still waiting for your signature. Your new personal link (the previous one no longer works):\n{lien}\n\nIt expires on {echeance}."}', 10)
) AS v(code, libelles, ordre) ON t.code = 'modele-courriel';
--rollback DELETE FROM referentiel_valeur WHERE code IN ('relance-sujet', 'relance-corps');
