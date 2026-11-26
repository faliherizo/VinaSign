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

# Organisation de démonstration, Municipalité de Rivière-Claire : son administratrice et sa préparatrice, créées par
# l'exploitation comme l'administrateur de la plateforme. Mots de passe provisoires dans secrets/comptes-demo.txt ;
# chaque compte enrôle son code à usage unique (TOTP) à sa première connexion.
ORG_NOM="Municipalité de Rivière-Claire (démonstration)"
sql() { docker exec -i vinasign-db psql -qtA -U "$(valeur VINASIGN_DB_UTILISATEUR)" -d vinasign; }
ORG="$(printf "SELECT id FROM organisation WHERE nom = '%s';\n" "$ORG_NOM" | sql)"
if [ -z "$ORG" ]; then
  PLAN="$(printf "SELECT v.code FROM referentiel_valeur v JOIN referentiel_type t ON t.id = v.type_id WHERE t.code = 'plan' AND v.tenant_id IS NULL ORDER BY v.ordre LIMIT 1;\n" | sql)"
  ORG="$(printf "INSERT INTO organisation (id, nom, statut, plan, cree_le, modifie_le) VALUES (gen_random_uuid(), '%s', 'active', '%s', now(), now()) RETURNING id;\n" "$ORG_NOM" "$PLAN" | sql | head -1)"
  echo "✓ organisation de démonstration créée"
fi
compte() { # $1 courriel, $2 prénom, $3 nom, $4 rôles (tableau SQL), $5 libellé
  printf "INSERT INTO utilisateur_portail (id, tenant_id, courriel, roles) SELECT gen_random_uuid(), '%s', '%s', %s
          WHERE NOT EXISTS (SELECT 1 FROM utilisateur_portail WHERE courriel = '%s');\n" "$ORG" "$1" "$4" "$1" | sql
  kc get users -r vinasign -q email="$1" --fields id | grep -q '"id"' && return 0
  kc create users -r vinasign -s username="$1" -s email="$1" -s emailVerified=true -s enabled=true -s firstName="$2" -s lastName="$3" >/dev/null
  local m; m="$(openssl rand -base64 24 | tr -d '\n/+=' | cut -c1-20)"
  docker exec -e MDP="$m" vinasign-keycloak sh -c '/opt/keycloak/bin/kcadm.sh set-password -r vinasign --username '"$1"' --new-password "$MDP" --temporary' >/dev/null
  ( umask 077; printf '%s (Rivière-Claire, démonstration) : %s / %s (provisoire)\n' "$5" "$1" "$m" >> "$ICI/secrets/comptes-demo.txt" )
  echo "✓ $5 créée (secrets/comptes-demo.txt)"
}
compte admin@riviere-claire.vinasign.vinaitsolution.com Camille Gagnon "ARRAY['administrateur-organisation']" "Administratrice de l'organisation"
compte preparation@riviere-claire.vinasign.vinaitsolution.com Léa Fortin "ARRAY['preparateur','auditeur']" "Préparatrice"
