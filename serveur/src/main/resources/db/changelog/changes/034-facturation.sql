--liquibase formatted sql

--changeset vinasign:034-facturation splitStatements:false
--comment: VS-121 — abonnement payé chez le prestataire (page de paiement hébergée : aucun numéro de carte chez VinaSign) et
--         factures émises par lui. VinaSign ne garde que les identifiants du prestataire, le plan, l'état et les liens vers
--         les factures hébergées. Les événements du prestataire, livrés au moins une fois, ne sont traités qu'une fois.
CREATE TABLE abonnement (
  tenant_id     UUID PRIMARY KEY REFERENCES organisation (id),
  prestataire   VARCHAR(30) NOT NULL,
  client        VARCHAR(100) NOT NULL CONSTRAINT uk_abonnement_client UNIQUE,
  reference     VARCHAR(100),
  plan          VARCHAR(60) NOT NULL,
  etat          VARCHAR(30) NOT NULL,
  modifie_le    TIMESTAMPTZ NOT NULL
);
ALTER TABLE abonnement ENABLE ROW LEVEL SECURITY;
CREATE POLICY organisation_acces ON abonnement TO "${roleApplicatif}"
  USING (tenant_id = NULLIF(current_setting('app.organisation', true), '')::uuid)
  WITH CHECK (tenant_id = NULLIF(current_setting('app.organisation', true), '')::uuid);
GRANT SELECT, INSERT, UPDATE ON abonnement TO "${roleApplicatif}";
CREATE TABLE facture (
  id            UUID PRIMARY KEY,
  tenant_id     UUID NOT NULL REFERENCES organisation (id),
  reference     VARCHAR(100) NOT NULL CONSTRAINT uk_facture_reference UNIQUE,
  numero        VARCHAR(60),
  montant       BIGINT NOT NULL,
  devise        CHAR(3) NOT NULL,
  url_hebergee  VARCHAR(1000),
  url_pdf       VARCHAR(1000),
  debut_periode TIMESTAMPTZ,
  fin_periode   TIMESTAMPTZ,
  payee_le      TIMESTAMPTZ NOT NULL
);
CREATE INDEX ix_facture_organisation ON facture (tenant_id, payee_le DESC);
ALTER TABLE facture ENABLE ROW LEVEL SECURITY;
CREATE POLICY organisation_acces ON facture TO "${roleApplicatif}"
  USING (tenant_id = NULLIF(current_setting('app.organisation', true), '')::uuid)
  WITH CHECK (tenant_id = NULLIF(current_setting('app.organisation', true), '')::uuid);
GRANT SELECT, INSERT ON facture TO "${roleApplicatif}";
-- Table de la plateforme : identifiants des événements déjà traités (déduplication), sans donnée d'organisation.
CREATE TABLE evenement_paiement (
  id         VARCHAR(100) PRIMARY KEY,
  recu_le    TIMESTAMPTZ NOT NULL
);
GRANT SELECT, INSERT ON evenement_paiement TO "${roleApplicatif}";
-- L'événement du prestataire désigne un client, pas une organisation : la fonction rend l'organisation de ce client.
CREATE FUNCTION abonnement_organisation(p_client TEXT) RETURNS UUID
  LANGUAGE sql STABLE SECURITY DEFINER SET search_path = public, pg_temp AS $$
  SELECT tenant_id FROM abonnement WHERE client = p_client;
$$;
REVOKE ALL ON FUNCTION abonnement_organisation(TEXT) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION abonnement_organisation(TEXT) TO "${roleApplicatif}";
--rollback DROP FUNCTION abonnement_organisation(TEXT); DROP TABLE evenement_paiement, facture, abonnement;
