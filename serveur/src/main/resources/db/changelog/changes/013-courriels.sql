--liquibase formatted sql

--changeset vinasign:013-1-boite-envoi splitStatements:false
--comment: VS-080 — boîte d'envoi des courriels : écrite dans la transaction de l'action, expédiée ensuite ; le corps (qui porte le lien) est effacé une fois envoyé.
CREATE TABLE courriel_sortant (
  id                  UUID PRIMARY KEY,
  tenant_id           UUID NOT NULL,
  destinataire        VARCHAR(320) NOT NULL,
  sujet               VARCHAR(300) NOT NULL,
  corps               TEXT,
  cree_le             TIMESTAMPTZ NOT NULL DEFAULT now(),
  tentatives          INT NOT NULL DEFAULT 0,
  prochaine_tentative TIMESTAMPTZ NOT NULL,
  envoye_le           TIMESTAMPTZ,
  abandonne_le        TIMESTAMPTZ
);
CREATE INDEX ix_courriel_du ON courriel_sortant (prochaine_tentative) WHERE envoye_le IS NULL AND abandonne_le IS NULL;
ALTER TABLE courriel_sortant ENABLE ROW LEVEL SECURITY;
CREATE POLICY organisation_acces ON courriel_sortant TO "${roleApplicatif}"
  USING (tenant_id = NULLIF(current_setting('app.organisation', true), '')::uuid)
  WITH CHECK (tenant_id = NULLIF(current_setting('app.organisation', true), '')::uuid);
GRANT INSERT ON courriel_sortant TO "${roleApplicatif}";

-- Exception motivée : l'expédition est faite par la plateforme pour toutes les organisations. Les courriels dus sont
-- réservés (verrou sauté par une expédition concurrente, échéance repoussée) pour qu'une autre réplique ne les renvoie pas.
CREATE FUNCTION courriel_dus(p_maintenant TIMESTAMPTZ, p_limite INT) RETURNS SETOF courriel_sortant
  LANGUAGE sql VOLATILE SECURITY DEFINER SET search_path = public, pg_temp AS $$
  UPDATE courriel_sortant c SET prochaine_tentative = p_maintenant + interval '5 minutes'
  WHERE c.id IN (SELECT id FROM courriel_sortant WHERE envoye_le IS NULL AND abandonne_le IS NULL AND prochaine_tentative <= p_maintenant
                 ORDER BY prochaine_tentative LIMIT p_limite FOR UPDATE SKIP LOCKED)
  RETURNING c.*;
$$;
CREATE FUNCTION courriel_envoye(p_id UUID, p_le TIMESTAMPTZ) RETURNS void
  LANGUAGE sql VOLATILE SECURITY DEFINER SET search_path = public, pg_temp AS
  'UPDATE courriel_sortant SET envoye_le = p_le, corps = NULL WHERE id = p_id';
CREATE FUNCTION courriel_echec(p_id UUID, p_tentatives INT, p_prochaine TIMESTAMPTZ, p_abandonne BOOLEAN) RETURNS void
  LANGUAGE sql VOLATILE SECURITY DEFINER SET search_path = public, pg_temp AS
  'UPDATE courriel_sortant SET tentatives = p_tentatives, prochaine_tentative = p_prochaine,
     abandonne_le = CASE WHEN p_abandonne THEN now() END, corps = CASE WHEN p_abandonne THEN NULL ELSE corps END WHERE id = p_id';
REVOKE ALL ON FUNCTION courriel_dus(TIMESTAMPTZ, INT), courriel_envoye(UUID, TIMESTAMPTZ), courriel_echec(UUID, INT, TIMESTAMPTZ, BOOLEAN) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION courriel_dus(TIMESTAMPTZ, INT), courriel_envoye(UUID, TIMESTAMPTZ), courriel_echec(UUID, INT, TIMESTAMPTZ, BOOLEAN)
  TO "${roleApplicatif}";
--rollback DROP FUNCTION courriel_echec(UUID, INT, TIMESTAMPTZ, BOOLEAN); DROP FUNCTION courriel_envoye(UUID, TIMESTAMPTZ); DROP FUNCTION courriel_dus(TIMESTAMPTZ, INT); DROP TABLE courriel_sortant;

--changeset vinasign:013-2-modeles-courriel
--comment: Modèles de courriel par langue (VS-080) ; une langue ou un modèle de plus est une donnée.
INSERT INTO referentiel_type (code, libelles) VALUES ('modele-courriel', '{"fr":"Modèle de courriel","en":"Email template"}');
INSERT INTO referentiel_valeur (type_id, code, libelles, ordre, attributs)
SELECT t.id, v.code, v.libelles::jsonb, v.ordre, '{}'::jsonb FROM referentiel_type t JOIN (VALUES
  ('invitation-sujet', '{"fr":"Document à signer : {reference}","en":"Document to sign: {reference}"}', 1),
  ('invitation-corps', '{"fr":"Bonjour {nom},\n\nUn document vous attend pour signature. Ouvrez ce lien personnel (ne le transférez pas) :\n{lien}\n\nIl expire le {echeance}. Aucun document n''est joint à ce courriel.","en":"Hello {nom},\n\nA document is waiting for your signature. Open this personal link (do not forward it):\n{lien}\n\nIt expires on {echeance}. No document is attached to this email."}', 2),
  ('confirmation-sujet', '{"fr":"Signature terminée : {reference}","en":"Signing complete: {reference}"}', 3),
  ('confirmation-corps', '{"fr":"Bonjour {nom},\n\nToutes les signatures du document {reference} ont été recueillies. L''expéditeur vous transmettra le document signé.","en":"Hello {nom},\n\nAll signatures for document {reference} have been collected. The sender will provide the signed document."}', 4)
) AS v(code, libelles, ordre) ON t.code = 'modele-courriel';
--rollback DELETE FROM referentiel_type WHERE code = 'modele-courriel';
