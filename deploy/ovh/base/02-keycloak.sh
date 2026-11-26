#!/bin/sh
# Base et compte propres à Keycloak (fournisseur d'identité du portail), créés au premier démarrage de la base.
set -eu
psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname postgres -v mdp="$KEYCLOAK_DB_MDP" <<'SQL'
SELECT format('CREATE ROLE keycloak LOGIN PASSWORD %L', :'mdp') WHERE NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'keycloak') \gexec
SELECT 'CREATE DATABASE keycloak OWNER keycloak' WHERE NOT EXISTS (SELECT 1 FROM pg_database WHERE datname = 'keycloak') \gexec
SQL
