#!/bin/sh
# Provisionne le compte applicatif au premier démarrage de la base de développement (VS-005) : LOGIN, sans aucun
# privilège, mot de passe tiré de .env. En production, Terraform fait de même avec un secret de Key Vault.
set -eu
psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" \
  -v role="$VINASIGN_DB_ROLE_APPLICATIF" -v mdp="$VINASIGN_DB_MDP_APPLICATIF" <<'SQL'
SELECT format('CREATE ROLE %I LOGIN NOSUPERUSER NOBYPASSRLS NOCREATEROLE NOCREATEDB PASSWORD %L', :'role', :'mdp')
WHERE NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = :'role') \gexec
SQL
