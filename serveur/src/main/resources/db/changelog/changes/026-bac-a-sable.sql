--liquibase formatted sql

--changeset vinasign:026-bac-a-sable splitStatements:false
--comment: VS-095 — réinitialisation d'un bac à sable : l'application est rattachée à une organisation neuve et vide. Rien n'est
--         effacé (le journal de preuve et ses ancrages restent intacts) ; les anciennes données deviennent invisibles par la RLS.
--         Seule une application d'un environnement « réinitialisable » (attribut du référentiel) peut l'être.
UPDATE referentiel_valeur v SET attributs = v.attributs || '{"reinitialisable":"true"}'::jsonb
FROM referentiel_type t WHERE v.type_id = t.id AND t.code = 'environnement-application' AND v.code = 'bac-a-sable' AND v.tenant_id IS NULL;

-- Exception motivée : l'application change d'organisation, ce que la RLS (WITH CHECK) interdit à bon droit dans le cas général.
CREATE FUNCTION bac_a_sable_reinitialiser(p_client TEXT, p_nouvelle UUID, p_le TIMESTAMPTZ) RETURNS UUID
  LANGUAGE plpgsql VOLATILE SECURITY DEFINER SET search_path = public, pg_temp AS $$
DECLARE a application_cliente; o organisation;
BEGIN
  SELECT * INTO a FROM application_cliente WHERE client_id = p_client AND statut = 'active';
  IF NOT FOUND OR NOT EXISTS (SELECT 1 FROM referentiel_valeur v JOIN referentiel_type t ON t.id = v.type_id
                              WHERE t.code = 'environnement-application' AND v.code = a.environnement AND v.attributs ->> 'reinitialisable' = 'true') THEN
    RETURN NULL;
  END IF;
  SELECT * INTO o FROM organisation WHERE id = a.tenant_id;
  INSERT INTO organisation (id, nom, statut, plan, cree_le, modifie_le) VALUES (p_nouvelle, left(o.nom, 180) || ' (bac à sable)', o.statut, o.plan, p_le, p_le);
  UPDATE application_cliente SET tenant_id = p_nouvelle WHERE id = a.id;
  RETURN p_nouvelle;
END $$;
REVOKE ALL ON FUNCTION bac_a_sable_reinitialiser(TEXT, UUID, TIMESTAMPTZ) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION bac_a_sable_reinitialiser(TEXT, UUID, TIMESTAMPTZ) TO "${roleApplicatif}";
--rollback DROP FUNCTION bac_a_sable_reinitialiser(TEXT, UUID, TIMESTAMPTZ);
