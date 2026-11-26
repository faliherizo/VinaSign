--liquibase formatted sql

--changeset vinasign:023-1-chaine splitStatements:false
--comment: VS-071 — journal de preuve inaltérable (porté de ChaineScellee, CLM) : chaque événement d'une enveloppe porte son rang et
--         l'empreinte du précédent ; la base les calcule à l'insertion, quel que soit le chemin d'écriture (application ou
--         fonction de plateforme). Le journal est en ajout seul, pour tous les rôles. Forme canonique, identique en Java :
--         précédente|enveloppe|rang|type|acteur|microsecondes epoch|données (jsonb::text), « | » remplacé par « ¦ ».
ALTER TABLE evenement_enveloppe
  ADD COLUMN sequence BIGINT,
  ADD COLUMN empreinte_precedente CHAR(64),
  ADD COLUMN empreinte CHAR(64),
  ADD COLUMN consigne_le TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp();

CREATE FUNCTION journal_empreinte(p_precedente TEXT, p_enveloppe UUID, p_sequence BIGINT, p_type TEXT, p_acteur TEXT, p_le TIMESTAMPTZ,
                                  p_donnees JSONB) RETURNS CHAR(64)
  LANGUAGE sql IMMUTABLE SET search_path = public, pg_temp AS $$
  SELECT encode(sha256(convert_to(concat_ws('|', p_precedente, p_enveloppe::text, p_sequence::text, replace(p_type, '|', '¦'),
         replace(p_acteur, '|', '¦'), (extract(epoch FROM p_le) * 1000000)::bigint::text, p_donnees::text), 'UTF8')), 'hex');
$$;

-- Événements déjà consignés : chaînés dans l'ordre où ils sont survenus.
DO $$
DECLARE r RECORD; prec CHAR(64); env UUID; seq BIGINT;
BEGIN
  FOR r IN SELECT * FROM evenement_enveloppe ORDER BY enveloppe_id, survenu_le, id LOOP
    IF env IS DISTINCT FROM r.enveloppe_id THEN env := r.enveloppe_id; prec := repeat('0', 64); seq := 0; END IF;
    seq := seq + 1;
    UPDATE evenement_enveloppe SET sequence = seq, empreinte_precedente = prec,
      empreinte = journal_empreinte(prec, r.enveloppe_id, seq, r.type, r.acteur, r.survenu_le, r.donnees) WHERE id = r.id;
    SELECT empreinte INTO prec FROM evenement_enveloppe WHERE id = r.id;
  END LOOP;
END $$;
ALTER TABLE evenement_enveloppe ALTER COLUMN sequence SET NOT NULL, ALTER COLUMN empreinte_precedente SET NOT NULL, ALTER COLUMN empreinte SET NOT NULL,
  ADD CONSTRAINT uk_evenement_sequence UNIQUE (enveloppe_id, sequence);
CREATE INDEX ix_evenement_consigne ON evenement_enveloppe (consigne_le, id);

-- Le rang et les empreintes viennent toujours de la base ; deux écritures concurrentes sur une même enveloppe sont
-- sérialisées par le verrou de sa ligne.
CREATE FUNCTION journal_chainer() RETURNS trigger
  LANGUAGE plpgsql SET search_path = public, pg_temp AS $$
DECLARE prec RECORD;
BEGIN
  PERFORM 1 FROM enveloppe WHERE id = NEW.enveloppe_id FOR UPDATE;
  SELECT sequence, empreinte INTO prec FROM evenement_enveloppe WHERE enveloppe_id = NEW.enveloppe_id ORDER BY sequence DESC LIMIT 1;
  NEW.sequence := COALESCE(prec.sequence, 0) + 1;
  NEW.empreinte_precedente := COALESCE(prec.empreinte, repeat('0', 64));
  NEW.consigne_le := clock_timestamp();
  NEW.empreinte := journal_empreinte(NEW.empreinte_precedente, NEW.enveloppe_id, NEW.sequence, NEW.type, NEW.acteur, NEW.survenu_le, NEW.donnees);
  RETURN NEW;
END $$;
CREATE TRIGGER tg_journal_chainer BEFORE INSERT ON evenement_enveloppe FOR EACH ROW EXECUTE FUNCTION journal_chainer();

CREATE FUNCTION journal_ajout_seul() RETURNS trigger
  LANGUAGE plpgsql AS $$
BEGIN
  RAISE EXCEPTION 'Le journal de preuve est en ajout seul (VS-071).' USING ERRCODE = 'insufficient_privilege';
END $$;
CREATE TRIGGER tg_journal_ajout_seul BEFORE UPDATE OR DELETE ON evenement_enveloppe FOR EACH ROW EXECUTE FUNCTION journal_ajout_seul();
--rollback DROP TRIGGER tg_journal_ajout_seul ON evenement_enveloppe; DROP TRIGGER tg_journal_chainer ON evenement_enveloppe; DROP FUNCTION journal_ajout_seul(); DROP FUNCTION journal_chainer(); ALTER TABLE evenement_enveloppe DROP CONSTRAINT uk_evenement_sequence, DROP COLUMN sequence, DROP COLUMN empreinte_precedente, DROP COLUMN empreinte, DROP COLUMN consigne_le; DROP FUNCTION journal_empreinte(TEXT, UUID, BIGINT, TEXT, TEXT, TIMESTAMPTZ, JSONB);

--changeset vinasign:023-2-ancrage splitStatements:false
--comment: VS-071 — ancrage horaire : l'empreinte agrégée des événements consignés depuis le précédent ancrage, toutes organisations,
--         signée et horodatée par le cachet de la plateforme. Aucune donnée d'organisation n'y figure : des empreintes seulement.
CREATE TABLE ancrage_journal (
  id                 UUID PRIMARY KEY,
  depuis             TIMESTAMPTZ NOT NULL,
  jusqua             TIMESTAMPTZ NOT NULL,
  nombre             INT NOT NULL,
  empreinte_agregee  CHAR(64) NOT NULL,
  signature          BYTEA NOT NULL,
  ancre_le           TIMESTAMPTZ NOT NULL
);
CREATE TRIGGER tg_ancrage_ajout_seul BEFORE UPDATE OR DELETE ON ancrage_journal FOR EACH ROW EXECUTE FUNCTION journal_ajout_seul();

-- Exception motivée : l'agrégat couvre toutes les organisations ; seules des empreintes en sortent.
CREATE FUNCTION journal_agreger(p_depuis TIMESTAMPTZ, p_jusqua TIMESTAMPTZ) RETURNS TABLE (nombre INT, empreinte_agregee CHAR(64))
  LANGUAGE sql STABLE SECURITY DEFINER SET search_path = public, pg_temp AS $$
  SELECT count(*)::int, encode(sha256(convert_to(COALESCE(string_agg(empreinte, '' ORDER BY consigne_le, id), ''), 'UTF8')), 'hex')
  FROM evenement_enveloppe WHERE consigne_le > p_depuis AND consigne_le <= p_jusqua;
$$;
REVOKE ALL ON FUNCTION journal_agreger(TIMESTAMPTZ, TIMESTAMPTZ) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION journal_agreger(TIMESTAMPTZ, TIMESTAMPTZ) TO "${roleApplicatif}";
GRANT SELECT, INSERT ON ancrage_journal TO "${roleApplicatif}";
--rollback DROP FUNCTION journal_agreger(TIMESTAMPTZ, TIMESTAMPTZ); DROP TABLE ancrage_journal;
