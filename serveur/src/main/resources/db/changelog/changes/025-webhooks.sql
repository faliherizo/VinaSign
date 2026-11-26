--liquibase formatted sql

--changeset vinasign:025-webhooks splitStatements:false
--comment: VS-091 — destinations de webhooks d'une application (secret chiffré, événements choisis) ; livraisons avec trace de chaque
--         tentative, reprise à délai croissant pendant 72 h. La boîte d'envoi (evenement_sortant) est le contrat entre les
--         contextes : écrite par les enveloppes avec leur événement, lue ici par des fonctions dédiées.
CREATE TABLE webhook_destination (
  id             UUID PRIMARY KEY,
  tenant_id      UUID NOT NULL,
  client_id      VARCHAR(80) NOT NULL,
  url            VARCHAR(2000) NOT NULL,
  secret_chiffre TEXT NOT NULL,
  evenements     TEXT[] NOT NULL,
  cree_le        TIMESTAMPTZ NOT NULL,
  retire_le      TIMESTAMPTZ
);
CREATE INDEX ix_webhook_destination_client ON webhook_destination (tenant_id, client_id) WHERE retire_le IS NULL;
CREATE TABLE livraison_webhook (
  id                   UUID PRIMARY KEY,
  tenant_id            UUID NOT NULL,
  destination_id       UUID NOT NULL REFERENCES webhook_destination (id),
  evenement_sortant_id UUID NOT NULL REFERENCES evenement_sortant (id),
  cree_le              TIMESTAMPTZ NOT NULL,
  tentatives           INT NOT NULL DEFAULT 0,
  prochaine_tentative  TIMESTAMPTZ NOT NULL,
  livre_le             TIMESTAMPTZ,
  abandonne_le         TIMESTAMPTZ,
  dernier_code         INT,
  CONSTRAINT uk_livraison UNIQUE (destination_id, evenement_sortant_id)
);
CREATE INDEX ix_livraison_due ON livraison_webhook (prochaine_tentative) WHERE livre_le IS NULL AND abandonne_le IS NULL;
DO $$
DECLARE t TEXT;
BEGIN
  FOREACH t IN ARRAY ARRAY['webhook_destination', 'livraison_webhook'] LOOP
    EXECUTE format('ALTER TABLE %I ENABLE ROW LEVEL SECURITY', t);
    EXECUTE format('CREATE POLICY organisation_acces ON %I TO "${roleApplicatif}" '
                   'USING (tenant_id = NULLIF(current_setting(''app.organisation'', true), '''')::uuid) '
                   'WITH CHECK (tenant_id = NULLIF(current_setting(''app.organisation'', true), '''')::uuid)', t);
  END LOOP;
END $$;
GRANT SELECT, INSERT, UPDATE ON webhook_destination, livraison_webhook TO "${roleApplicatif}";
ALTER TABLE evenement_sortant ADD COLUMN reparti_le TIMESTAMPTZ;
CREATE INDEX ix_evenement_sortant_a_repartir ON evenement_sortant (cree_le) WHERE reparti_le IS NULL;

-- Exception motivée : la répartition et la livraison sont faites par la plateforme, pour toutes les organisations.
CREATE FUNCTION webhooks_repartir(p_le TIMESTAMPTZ) RETURNS INT
  LANGUAGE plpgsql VOLATILE SECURITY DEFINER SET search_path = public, pg_temp AS $$
DECLARE n INT;
BEGIN
  WITH s AS (UPDATE evenement_sortant SET reparti_le = p_le WHERE reparti_le IS NULL RETURNING *),
       l AS (INSERT INTO livraison_webhook (id, tenant_id, destination_id, evenement_sortant_id, cree_le, prochaine_tentative)
             SELECT gen_random_uuid(), s.tenant_id, d.id, s.id, s.cree_le, p_le FROM s
             JOIN webhook_destination d ON d.tenant_id = s.tenant_id AND d.client_id = s.client_id AND d.retire_le IS NULL
                                         AND (cardinality(d.evenements) = 0 OR s.nom = ANY (d.evenements))
             RETURNING 1)
  SELECT count(*) INTO n FROM l;
  RETURN n;
END $$;

CREATE FUNCTION webhooks_dus(p_le TIMESTAMPTZ, p_limite INT)
  RETURNS TABLE (id UUID, tenant_id UUID, destination_id UUID, evenement_sortant_id UUID, cree_le TIMESTAMPTZ, tentatives INT,
                 prochaine_tentative TIMESTAMPTZ, url VARCHAR, secret_chiffre TEXT, nom VARCHAR, enveloppe_id UUID, survenu_le TIMESTAMPTZ)
  LANGUAGE sql STABLE SECURITY DEFINER SET search_path = public, pg_temp AS $$
  SELECT l.id, l.tenant_id, l.destination_id, l.evenement_sortant_id, l.cree_le, l.tentatives, l.prochaine_tentative, d.url, d.secret_chiffre,
         s.nom, e.enveloppe_id, e.survenu_le
  FROM livraison_webhook l
  JOIN webhook_destination d ON d.id = l.destination_id AND d.retire_le IS NULL
  JOIN evenement_sortant s ON s.id = l.evenement_sortant_id
  JOIN evenement_enveloppe e ON e.id = s.evenement_id
  WHERE l.livre_le IS NULL AND l.abandonne_le IS NULL AND l.prochaine_tentative <= p_le
  ORDER BY l.prochaine_tentative LIMIT p_limite;
$$;
REVOKE ALL ON FUNCTION webhooks_repartir(TIMESTAMPTZ) FROM PUBLIC;
REVOKE ALL ON FUNCTION webhooks_dus(TIMESTAMPTZ, INT) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION webhooks_repartir(TIMESTAMPTZ) TO "${roleApplicatif}";
GRANT EXECUTE ON FUNCTION webhooks_dus(TIMESTAMPTZ, INT) TO "${roleApplicatif}";
--rollback DROP FUNCTION webhooks_dus(TIMESTAMPTZ, INT); DROP FUNCTION webhooks_repartir(TIMESTAMPTZ); ALTER TABLE evenement_sortant DROP COLUMN reparti_le; DROP TABLE livraison_webhook; DROP TABLE webhook_destination;
