#!/usr/bin/env bash
# Comptes de la démonstration (idempotent) : l'administrateur de la plateforme VinaSign, créé par l'exploitation comme le
# prévoit VS-014 (aucune API ne le crée), et son identité dans Keycloak. Son mot de passe initial, tiré au hasard, est
# rangé dans secrets/comptes-demo.txt (600) ; à la première connexion, il enrôle son code à usage unique (TOTP).
# Les organisations se créent ensuite par l'inscription du portail ; leurs utilisateurs, par leur administrateur.
set -euo pipefail
ICI="$(cd "$(dirname "$0")" && pwd)"
valeur() { grep -E "^$1=" "$ICI/.env.vinasign" | cut -d= -f2-; }
COURRIEL=plateforme@vinasign.vinaitsolution.com
kc() { docker exec -i vinasign-keycloak /opt/keycloak/bin/kcadm.sh "$@"; }

printf "INSERT INTO utilisateur_portail (id, courriel, roles) SELECT gen_random_uuid(), '%s', ARRAY['administrateur-plateforme']
        WHERE NOT EXISTS (SELECT 1 FROM utilisateur_portail WHERE courriel = '%s');\n" "$COURRIEL" "$COURRIEL" \
  | docker exec -i vinasign-db psql -q -U "$(valeur VINASIGN_DB_UTILISATEUR)" -d vinasign

if ! kc get users -r vinasign -q email=$COURRIEL --fields id | grep -q '"id"'; then
  kc create users -r vinasign -s username=$COURRIEL -s email=$COURRIEL -s emailVerified=true -s enabled=true \
    -s firstName=Administration -s lastName=Plateforme >/dev/null
  m="$(openssl rand -base64 24 | tr -d '\n/+=' | cut -c1-20)"
  docker exec -e MDP="$m" vinasign-keycloak sh -c '/opt/keycloak/bin/kcadm.sh set-password -r vinasign --username '"$COURRIEL"' --new-password "$MDP" --temporary' >/dev/null
  ( umask 077; printf 'Administrateur de la plateforme (https://vinasign.vinaitsolution.com) : %s / %s (provisoire)\n' "$COURRIEL" "$m" >> "$ICI/secrets/comptes-demo.txt" )
  echo "✓ administrateur de la plateforme créé (secrets/comptes-demo.txt)"
fi
