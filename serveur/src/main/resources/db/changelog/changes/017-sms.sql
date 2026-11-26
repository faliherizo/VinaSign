--liquibase formatted sql

--changeset vinasign:017-1-sms splitStatements:false
--comment: VS-081 — téléphone chiffré du signataire ; boîte d'envoi des SMS (numéro chiffré, texte effacé après envoi) ; SMS simulés du bac à sable.
ALTER TABLE signataire ADD COLUMN telephone_chiffre BYTEA;
CREATE TABLE sms_sortant (
  id                  UUID PRIMARY KEY,
  tenant_id           UUID NOT NULL,
  numero_chiffre      BYTEA NOT NULL,
  texte               TEXT,
  cree_le             TIMESTAMPTZ NOT NULL DEFAULT now(),
  tentatives          INT NOT NULL DEFAULT 0,
  prochaine_tentative TIMESTAMPTZ NOT NULL,
  envoye_le           TIMESTAMPTZ,
  abandonne_le        TIMESTAMPTZ
);
ALTER TABLE sms_sortant ENABLE ROW LEVEL SECURITY;
CREATE POLICY organisation_acces ON sms_sortant TO "${roleApplicatif}"
  USING (tenant_id = NULLIF(current_setting('app.organisation', true), '')::uuid)
  WITH CHECK (tenant_id = NULLIF(current_setting('app.organisation', true), '')::uuid);
GRANT INSERT ON sms_sortant TO "${roleApplicatif}";
CREATE FUNCTION sms_dus(p_maintenant TIMESTAMPTZ, p_limite INT) RETURNS SETOF sms_sortant
  LANGUAGE sql VOLATILE SECURITY DEFINER SET search_path = public, pg_temp AS $$
  UPDATE sms_sortant s SET prochaine_tentative = p_maintenant + interval '5 minutes'
  WHERE s.id IN (SELECT id FROM sms_sortant WHERE envoye_le IS NULL AND abandonne_le IS NULL AND prochaine_tentative <= p_maintenant
                 ORDER BY prochaine_tentative LIMIT p_limite FOR UPDATE SKIP LOCKED)
  RETURNING s.*;
$$;
CREATE FUNCTION sms_envoye(p_id UUID, p_le TIMESTAMPTZ) RETURNS void
  LANGUAGE sql VOLATILE SECURITY DEFINER SET search_path = public, pg_temp AS 'UPDATE sms_sortant SET envoye_le = p_le, texte = NULL WHERE id = p_id';
CREATE FUNCTION sms_echec(p_id UUID, p_tentatives INT, p_prochaine TIMESTAMPTZ, p_abandonne BOOLEAN) RETURNS void
  LANGUAGE sql VOLATILE SECURITY DEFINER SET search_path = public, pg_temp AS
  'UPDATE sms_sortant SET tentatives = p_tentatives, prochaine_tentative = p_prochaine,
     abandonne_le = CASE WHEN p_abandonne THEN now() END, texte = CASE WHEN p_abandonne THEN NULL ELSE texte END WHERE id = p_id';
REVOKE ALL ON FUNCTION sms_dus(TIMESTAMPTZ, INT), sms_envoye(UUID, TIMESTAMPTZ), sms_echec(UUID, INT, TIMESTAMPTZ, BOOLEAN) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION sms_dus(TIMESTAMPTZ, INT), sms_envoye(UUID, TIMESTAMPTZ), sms_echec(UUID, INT, TIMESTAMPTZ, BOOLEAN) TO "${roleApplicatif}";
-- SMS simulés (poste, bac à sable) : écrits par le simulateur, lus par les tests comme le ferait le destinataire.
CREATE TABLE sms_simule (id BIGSERIAL PRIMARY KEY, numero VARCHAR(20) NOT NULL, texte TEXT NOT NULL, recu_le TIMESTAMPTZ NOT NULL DEFAULT now());
GRANT INSERT ON sms_simule TO "${roleApplicatif}";
GRANT USAGE ON SEQUENCE sms_simule_id_seq TO "${roleApplicatif}";
--rollback DROP TABLE sms_simule; DROP FUNCTION sms_echec(UUID, INT, TIMESTAMPTZ, BOOLEAN); DROP FUNCTION sms_envoye(UUID, TIMESTAMPTZ); DROP FUNCTION sms_dus(TIMESTAMPTZ, INT); DROP TABLE sms_sortant; ALTER TABLE signataire DROP COLUMN telephone_chiffre;

--changeset vinasign:017-2-modele-sms
INSERT INTO referentiel_valeur (type_id, code, libelles, ordre, attributs)
SELECT t.id, v.code, v.libelles::jsonb, v.ordre, '{}'::jsonb FROM referentiel_type t JOIN (VALUES
  ('code-sms-sujet', '{"fr":"Code VinaSign","en":"VinaSign code"}', 7),
  ('code-sms-corps', '{"fr":"VinaSign : votre code est {code}. Ne le communiquez à personne.","en":"VinaSign: your code is {code}. Do not share it."}', 8)
) AS v(code, libelles, ordre) ON t.code = 'modele-courriel';
--rollback DELETE FROM referentiel_valeur WHERE code IN ('code-sms-sujet', 'code-sms-corps');
