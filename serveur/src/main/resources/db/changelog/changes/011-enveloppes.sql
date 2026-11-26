--liquibase formatted sql

--changeset vinasign:011-1-enveloppes splitStatements:false
--comment: VS-020 à VS-025 — enveloppes, signataires, versions immuables, dépôts, journal, boîte d'envoi, idempotence. Tout isolé par organisation.
CREATE TABLE enveloppe (
  id                UUID PRIMARY KEY,
  tenant_id         UUID NOT NULL REFERENCES organisation (id),
  client_id         VARCHAR(80)  NOT NULL,
  reference_externe VARCHAR(200) NOT NULL,
  mode              VARCHAR(30)  NOT NULL,
  statut            VARCHAR(30)  NOT NULL,
  expire_le         TIMESTAMPTZ  NOT NULL,
  cree_le           TIMESTAMPTZ  NOT NULL,
  modifie_le        TIMESTAMPTZ  NOT NULL,
  CONSTRAINT uk_enveloppe_reference UNIQUE (client_id, reference_externe)
);
CREATE INDEX ix_enveloppe_echeance ON enveloppe (expire_le) WHERE statut = 'envoyee';
CREATE INDEX ix_enveloppe_organisation_mois ON enveloppe (tenant_id, cree_le);

CREATE TABLE signataire (
  id            UUID PRIMARY KEY,
  tenant_id     UUID NOT NULL,
  enveloppe_id  UUID NOT NULL REFERENCES enveloppe (id),
  nom           VARCHAR(200) NOT NULL,
  courriel      VARCHAR(320),
  langue        VARCHAR(30)  NOT NULL,
  type          VARCHAR(60)  NOT NULL,
  ordre         INT          NOT NULL,
  sujet_externe VARCHAR(300),
  statut        VARCHAR(30)  NOT NULL,
  agi_le        TIMESTAMPTZ
);
CREATE INDEX ix_signataire_enveloppe ON signataire (enveloppe_id);

-- Versions immuables (VS-022) : ajout seul, une version par nature et par enveloppe.
CREATE TABLE version_document (
  id           UUID PRIMARY KEY,
  tenant_id    UUID NOT NULL,
  enveloppe_id UUID NOT NULL REFERENCES enveloppe (id),
  nature       VARCHAR(30) NOT NULL,
  cle_objet    VARCHAR(400) NOT NULL,
  sha256       CHAR(64) NOT NULL,
  taille       BIGINT NOT NULL,
  pages        INT NOT NULL,
  cree_le      TIMESTAMPTZ NOT NULL,
  CONSTRAINT uk_version_nature UNIQUE (enveloppe_id, nature)
);

CREATE TABLE depot_document (
  id              UUID PRIMARY KEY,
  tenant_id       UUID NOT NULL,
  enveloppe_id    UUID NOT NULL REFERENCES enveloppe (id),
  empreinte_jeton CHAR(64) NOT NULL CONSTRAINT uk_depot_jeton UNIQUE,
  cle_objet       VARCHAR(400) NOT NULL,
  expire_le       TIMESTAMPTZ NOT NULL,
  recu_le         TIMESTAMPTZ,
  confirme_le     TIMESTAMPTZ
);

CREATE TABLE evenement_enveloppe (
  id           UUID PRIMARY KEY,
  tenant_id    UUID NOT NULL,
  enveloppe_id UUID NOT NULL REFERENCES enveloppe (id),
  type         VARCHAR(60) NOT NULL,
  acteur       VARCHAR(200) NOT NULL,
  survenu_le   TIMESTAMPTZ NOT NULL,
  donnees      JSONB NOT NULL DEFAULT '{}'
);
CREATE INDEX ix_evenement_enveloppe ON evenement_enveloppe (enveloppe_id, survenu_le);

-- Boîte d'envoi transactionnelle : écrite dans la même transaction que l'événement, livrée par les webhooks (VS-091).
CREATE TABLE evenement_sortant (
  id           UUID PRIMARY KEY,
  tenant_id    UUID NOT NULL,
  client_id    VARCHAR(80) NOT NULL,
  evenement_id UUID NOT NULL REFERENCES evenement_enveloppe (id),
  nom          VARCHAR(80) NOT NULL,
  cree_le      TIMESTAMPTZ NOT NULL,
  livre_le     TIMESTAMPTZ
);
CREATE INDEX ix_evenement_sortant_a_livrer ON evenement_sortant (cree_le) WHERE livre_le IS NULL;

-- Idempotence (VS-020) : même clé et même corps, même réponse ; même clé et autre corps, refus.
CREATE TABLE requete_idempotente (
  tenant_id UUID NOT NULL,
  client_id VARCHAR(80) NOT NULL,
  cle       VARCHAR(200) NOT NULL,
  empreinte CHAR(64) NOT NULL,
  statut    INT,
  reponse   TEXT,
  expire_le TIMESTAMPTZ NOT NULL,
  PRIMARY KEY (client_id, cle)
);

DO $$
DECLARE t TEXT;
BEGIN
  FOREACH t IN ARRAY ARRAY['enveloppe', 'signataire', 'version_document', 'depot_document', 'evenement_enveloppe', 'evenement_sortant',
                           'requete_idempotente'] LOOP
    EXECUTE format('ALTER TABLE %I ENABLE ROW LEVEL SECURITY', t);
    EXECUTE format('CREATE POLICY organisation_acces ON %I TO "${roleApplicatif}" '
                   'USING (tenant_id = NULLIF(current_setting(''app.organisation'', true), '''')::uuid) '
                   'WITH CHECK (tenant_id = NULLIF(current_setting(''app.organisation'', true), '''')::uuid)', t);
  END LOOP;
END $$;
GRANT SELECT, INSERT, UPDATE ON enveloppe, signataire, depot_document, requete_idempotente TO "${roleApplicatif}";
-- Versions, journal et boîte d'envoi : ajout seul pour l'application (la livraison des webhooks aura sa fonction).
GRANT SELECT, INSERT ON version_document, evenement_enveloppe, evenement_sortant TO "${roleApplicatif}";
GRANT DELETE ON signataire, requete_idempotente TO "${roleApplicatif}";

-- Exception motivée : la réception par URL présignée n'est authentifiée que par le jeton (aucune organisation courante).
-- Elle n'accepte qu'un dépôt non expiré, encore vide, d'une enveloppe en brouillon (une annulation invalide les dépôts).
CREATE FUNCTION enveloppe_recevoir_depot(p_empreinte TEXT, p_maintenant TIMESTAMPTZ) RETURNS SETOF depot_document
  LANGUAGE sql VOLATILE SECURITY DEFINER SET search_path = public, pg_temp AS $$
  UPDATE depot_document d SET recu_le = p_maintenant
  FROM enveloppe e
  WHERE d.empreinte_jeton = p_empreinte AND d.recu_le IS NULL AND d.expire_le > p_maintenant
    AND e.id = d.enveloppe_id AND e.statut = 'brouillon'
  RETURNING d.*;
$$;

-- Exception motivée : l'expiration à échéance est faite par la plateforme, pour toutes les organisations.
CREATE FUNCTION enveloppe_echues(p_maintenant TIMESTAMPTZ, p_limite INT) RETURNS SETOF enveloppe
  LANGUAGE sql STABLE SECURITY DEFINER SET search_path = public, pg_temp AS $$
  SELECT * FROM enveloppe WHERE statut = 'envoyee' AND expire_le <= p_maintenant ORDER BY expire_le LIMIT p_limite;
$$;

CREATE FUNCTION enveloppe_expirer(p_id UUID, p_le TIMESTAMPTZ, p_evenement UUID, p_type TEXT, p_acteur TEXT) RETURNS boolean
  LANGUAGE plpgsql VOLATILE SECURITY DEFINER SET search_path = public, pg_temp AS $$
DECLARE e enveloppe;
BEGIN
  UPDATE enveloppe SET statut = 'expiree', modifie_le = p_le WHERE id = p_id AND statut = 'envoyee' AND expire_le <= p_le RETURNING * INTO e;
  IF NOT FOUND THEN RETURN false; END IF;
  INSERT INTO evenement_enveloppe (id, tenant_id, enveloppe_id, type, acteur, survenu_le) VALUES (p_evenement, e.tenant_id, e.id, p_type, p_acteur, p_le);
  INSERT INTO evenement_sortant (id, tenant_id, client_id, evenement_id, nom, cree_le)
  SELECT gen_random_uuid(), e.tenant_id, e.client_id, p_evenement, v.attributs ->> 'webhook', p_le
  FROM referentiel_valeur v JOIN referentiel_type t ON t.id = v.type_id
  WHERE t.code = 'evenement-enveloppe' AND v.code = p_type AND v.tenant_id IS NULL AND v.actif AND v.attributs ? 'webhook';
  RETURN true;
END $$;
REVOKE ALL ON FUNCTION enveloppe_recevoir_depot(TEXT, TIMESTAMPTZ), enveloppe_echues(TIMESTAMPTZ, INT),
  enveloppe_expirer(UUID, TIMESTAMPTZ, UUID, TEXT, TEXT) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION enveloppe_recevoir_depot(TEXT, TIMESTAMPTZ), enveloppe_echues(TIMESTAMPTZ, INT),
  enveloppe_expirer(UUID, TIMESTAMPTZ, UUID, TEXT, TEXT) TO "${roleApplicatif}";
--rollback DROP FUNCTION enveloppe_expirer(UUID, TIMESTAMPTZ, UUID, TEXT, TEXT); DROP FUNCTION enveloppe_echues(TIMESTAMPTZ, INT); DROP FUNCTION enveloppe_recevoir_depot(TEXT, TIMESTAMPTZ); DROP TABLE requete_idempotente, evenement_sortant, evenement_enveloppe, depot_document, version_document, signataire, enveloppe;

--changeset vinasign:011-2-referentiels-enveloppes
--comment: Types de signataire (exigences en attributs), événements d'enveloppe (webhook publié en attribut). « en-cours » n'est pas un état de la machine du domaine : retiré.
INSERT INTO referentiel_type (code, libelles) VALUES
  ('type-signataire',     '{"fr":"Type de signataire","en":"Signer type"}'),
  ('evenement-enveloppe', '{"fr":"Événement d''enveloppe","en":"Envelope event"}');
INSERT INTO referentiel_valeur (type_id, code, libelles, ordre, attributs)
SELECT t.id, v.code, v.libelles::jsonb, v.ordre, v.attributs::jsonb FROM referentiel_type t JOIN (VALUES
  ('type-signataire', 'invite',                  '{"fr":"Invité","en":"Guest"}', 1, '{"exigeCourriel":"true"}'),
  ('type-signataire', 'utilisateur-application', '{"fr":"Utilisateur de l''application","en":"Application user"}', 2, '{"exigeSujetExterne":"true"}'),
  ('evenement-enveloppe', 'enveloppe-creee',     '{"fr":"Enveloppe créée","en":"Envelope created"}', 1, '{}'),
  ('evenement-enveloppe', 'signataires-definis', '{"fr":"Signataires définis","en":"Signers set"}', 2, '{}'),
  ('evenement-enveloppe', 'document-depose',     '{"fr":"Document déposé","en":"Document uploaded"}', 3, '{}'),
  ('evenement-enveloppe', 'enveloppe-envoyee',   '{"fr":"Enveloppe envoyée","en":"Envelope sent"}', 4, '{"webhook":"envelope.sent"}'),
  ('evenement-enveloppe', 'enveloppe-annulee',   '{"fr":"Enveloppe annulée","en":"Envelope cancelled"}', 5, '{"webhook":"envelope.cancelled"}'),
  ('evenement-enveloppe', 'enveloppe-expiree',   '{"fr":"Enveloppe expirée","en":"Envelope expired"}', 6, '{"webhook":"envelope.expired"}')
) AS v(type, code, libelles, ordre, attributs) ON v.type = t.code;
UPDATE referentiel_valeur SET actif = false
WHERE code = 'en-cours' AND tenant_id IS NULL AND type_id = (SELECT id FROM referentiel_type WHERE code = 'statut-enveloppe');
--rollback DELETE FROM referentiel_type WHERE code IN ('type-signataire', 'evenement-enveloppe');
