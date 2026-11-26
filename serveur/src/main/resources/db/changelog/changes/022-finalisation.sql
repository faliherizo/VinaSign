--liquibase formatted sql

--changeset vinasign:022-finalisation splitStatements:false
--comment: VS-070 — finalisation par la plateforme : enveloppes envoyées entièrement signées, toutes organisations (exception motivée,
--         comme l'expiration) ; événement « enveloppe complétée » livré par webhook ; confirmation aux signataires.
CREATE FUNCTION enveloppes_a_finaliser(p_limite INT) RETURNS TABLE (id UUID, tenant_id UUID)
  LANGUAGE sql STABLE SECURITY DEFINER SET search_path = public, pg_temp AS $$
  SELECT e.id, e.tenant_id FROM enveloppe e
  WHERE e.statut = 'envoyee'
    AND EXISTS (SELECT 1 FROM signataire s WHERE s.enveloppe_id = e.id)
    AND NOT EXISTS (SELECT 1 FROM signataire s WHERE s.enveloppe_id = e.id AND s.statut <> 'signe')
  ORDER BY e.modifie_le LIMIT p_limite;
$$;
REVOKE ALL ON FUNCTION enveloppes_a_finaliser(INT) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION enveloppes_a_finaliser(INT) TO "${roleApplicatif}";
INSERT INTO referentiel_valeur (type_id, code, libelles, ordre, attributs)
SELECT t.id, 'enveloppe-completee', '{"fr":"Enveloppe complétée","en":"Envelope completed"}'::jsonb, 20, '{"webhook":"envelope.completed"}'::jsonb
FROM referentiel_type t WHERE t.code = 'evenement-enveloppe'
  AND NOT EXISTS (SELECT 1 FROM referentiel_valeur v WHERE v.type_id = t.id AND v.code = 'enveloppe-completee');
--rollback DROP FUNCTION enveloppes_a_finaliser(INT); DELETE FROM referentiel_valeur WHERE code = 'enveloppe-completee';
