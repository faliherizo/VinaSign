--liquibase formatted sql

--changeset vinasign:003-1-role-applicatif runOnChange:true splitStatements:false
--comment: VS-005 — le compte applicatif est provisionné hors migrations (avec son mot de passe) ; ici, on vérifie qu'il est inoffensif et on lui donne l'accès au schéma. Nom tiré de la configuration.
DO $$
DECLARE r pg_roles%ROWTYPE;
BEGIN
  SELECT * INTO r FROM pg_roles WHERE rolname = '${roleApplicatif}';
  IF NOT FOUND THEN
    RAISE EXCEPTION 'Compte applicatif « % » absent : le provisionner (LOGIN, mot de passe) avant les migrations.', '${roleApplicatif}';
  END IF;
  IF r.rolsuper OR r.rolbypassrls OR r.rolcreaterole OR r.rolcreatedb THEN
    RAISE EXCEPTION 'Compte applicatif « % » trop privilégié : il doit être NOSUPERUSER NOBYPASSRLS NOCREATEROLE NOCREATEDB.', '${roleApplicatif}';
  END IF;
  EXECUTE format('GRANT USAGE ON SCHEMA public TO %I', '${roleApplicatif}');
END
$$;
