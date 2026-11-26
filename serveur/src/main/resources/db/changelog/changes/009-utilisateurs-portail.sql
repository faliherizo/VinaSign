--liquibase formatted sql

--changeset vinasign:009-1-utilisateurs-portail splitStatements:false
--comment: VS-014 — utilisateurs du portail : invités par courriel, liés à leur identité OIDC à la première connexion.
CREATE TABLE utilisateur_portail (
  id        UUID PRIMARY KEY,
  tenant_id UUID REFERENCES organisation (id),
  courriel  VARCHAR(320) NOT NULL CONSTRAINT uk_utilisateur_courriel UNIQUE,
  emetteur  VARCHAR(300),
  sujet     VARCHAR(300),
  roles     TEXT[] NOT NULL,
  actif     BOOLEAN NOT NULL DEFAULT true,
  lie_le    TIMESTAMPTZ,
  CONSTRAINT uk_utilisateur_identite UNIQUE (emetteur, sujet)
);
-- Un utilisateur sans organisation (tenant_id nul) est un utilisateur de la plateforme : il n'est visible d'aucune
-- organisation et n'est créé que par l'exploitation.
GRANT SELECT, INSERT, UPDATE ON utilisateur_portail TO "${roleApplicatif}";
ALTER TABLE utilisateur_portail ENABLE ROW LEVEL SECURITY;
CREATE POLICY organisation_acces ON utilisateur_portail TO "${roleApplicatif}"
  USING (tenant_id = NULLIF(current_setting('app.organisation', true), '')::uuid)
  WITH CHECK (tenant_id = NULLIF(current_setting('app.organisation', true), '')::uuid);

-- Exception motivée à la RLS (comme acces_application_active) : à l'authentification, l'organisation n'est connue
-- qu'une fois l'utilisateur trouvé. Rend l'utilisateur de cette identité ; à défaut, lie l'utilisateur invité dont le
-- courriel est attesté par le fournisseur et qui n'est encore lié à aucune identité.
CREATE FUNCTION acces_utilisateur_authentifie(p_emetteur TEXT, p_sujet TEXT, p_courriel TEXT) RETURNS SETOF utilisateur_portail
  LANGUAGE plpgsql VOLATILE SECURITY DEFINER SET search_path = public, pg_temp AS $$
BEGIN
  RETURN QUERY SELECT * FROM utilisateur_portail WHERE emetteur = p_emetteur AND sujet = p_sujet;
  IF NOT FOUND AND p_courriel IS NOT NULL THEN
    RETURN QUERY UPDATE utilisateur_portail SET emetteur = p_emetteur, sujet = p_sujet, lie_le = now()
      WHERE courriel = lower(p_courriel) AND sujet IS NULL AND actif RETURNING *;
  END IF;
END $$;
REVOKE ALL ON FUNCTION acces_utilisateur_authentifie(TEXT, TEXT, TEXT) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION acces_utilisateur_authentifie(TEXT, TEXT, TEXT) TO "${roleApplicatif}";

-- Exception motivée : l'administration de la plateforme invite le premier administrateur d'une organisation sans agir
-- en son nom. La fonction n'accepte qu'une organisation active.
CREATE FUNCTION acces_inviter_pour_plateforme(p_id UUID, p_organisation UUID, p_courriel TEXT, p_roles TEXT[]) RETURNS boolean
  LANGUAGE plpgsql VOLATILE SECURITY DEFINER SET search_path = public, pg_temp AS $$
BEGIN
  INSERT INTO utilisateur_portail (id, tenant_id, courriel, roles)
  SELECT p_id, o.id, lower(p_courriel), p_roles FROM organisation o WHERE o.id = p_organisation AND o.statut = 'active';
  RETURN FOUND;
END $$;
REVOKE ALL ON FUNCTION acces_inviter_pour_plateforme(UUID, UUID, TEXT, TEXT[]) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION acces_inviter_pour_plateforme(UUID, UUID, TEXT, TEXT[]) TO "${roleApplicatif}";
--rollback DROP FUNCTION acces_inviter_pour_plateforme(UUID, UUID, TEXT, TEXT[]); DROP FUNCTION acces_utilisateur_authentifie(TEXT, TEXT, TEXT); DROP TABLE utilisateur_portail;

--changeset vinasign:009-2-referentiels-utilisateurs
--comment: Rôles du portail (exigeMfa : accordé seulement avec une MFA attestée ; portee : qui peut l'attribuer) et actions.
INSERT INTO referentiel_type (code, libelles) VALUES ('role-portail', '{"fr":"Rôle du portail","en":"Portal role"}');
INSERT INTO referentiel_valeur (type_id, code, libelles, ordre, attributs)
SELECT t.id, v.code, v.libelles::jsonb, v.ordre, v.attributs::jsonb FROM referentiel_type t JOIN (VALUES
  ('role-portail', 'administrateur-organisation', '{"fr":"Administrateur","en":"Administrator"}', 1, '{"exigeMfa":"true","portee":"organisation"}'),
  ('role-portail', 'preparateur',                 '{"fr":"Préparateur","en":"Preparer"}', 2, '{"exigeMfa":"false","portee":"organisation"}'),
  ('role-portail', 'auditeur',                    '{"fr":"Auditeur","en":"Auditor"}', 3, '{"exigeMfa":"false","portee":"organisation"}'),
  ('role-portail', 'administrateur-plateforme',   '{"fr":"Administrateur de la plateforme","en":"Platform administrator"}', 4, '{"exigeMfa":"true","portee":"plateforme"}'),
  ('action-audit', 'utilisateur-invite',          '{"fr":"Utilisateur invité","en":"User invited"}', 20, '{}'),
  ('action-audit', 'roles-utilisateur-modifies',  '{"fr":"Rôles d''un utilisateur modifiés","en":"User roles changed"}', 21, '{}'),
  ('action-audit', 'utilisateur-desactive',       '{"fr":"Utilisateur désactivé","en":"User deactivated"}', 22, '{}')
) AS v(type, code, libelles, ordre, attributs) ON v.type = t.code;
--rollback DELETE FROM referentiel_valeur WHERE code IN ('utilisateur-invite', 'roles-utilisateur-modifies', 'utilisateur-desactive'); DELETE FROM referentiel_type WHERE code = 'role-portail';
