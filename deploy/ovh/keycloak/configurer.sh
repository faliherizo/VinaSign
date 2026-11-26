#!/usr/bin/env bash
# Fournisseur d'identité du portail (VS-ADR-009) : royaume « vinasign » de Keycloak, configuré à chaque déploiement
# (idempotent). Le portail est un client public à PKCE ; le serveur VinaSign exige l'audience api://vinasign, un courriel
# attesté et, pour un rôle à MFA, « otp » dans amr. amr est tiré des étapes réellement franchies (mappeur AMR de Keycloak,
# référence posée sur le formulaire de mot de passe et sur celui du code) : jamais une valeur fixe. Tout compte enrôle un
# code à usage unique (TOTP) à sa première connexion.
set -euo pipefail
ICI="$(cd "$(dirname "$0")/.." && pwd)"
valeur() { grep -E "^$1=" "$ICI/.env.vinasign" | cut -d= -f2-; }
PORTAIL=https://vinasign.vinaitsolution.com
R=vinasign
kc() { docker exec -i vinasign-keycloak /opt/keycloak/bin/kcadm.sh "$@"; }

# Attente du démarrage, puis session d'administration (mot de passe passé par l'environnement du conteneur).
for i in $(seq 1 60); do
  docker exec -e KC_MDP="$(valeur KEYCLOAK_ADMIN_MDP)" vinasign-keycloak sh -c \
    '/opt/keycloak/bin/kcadm.sh config credentials --server http://localhost:8080 --realm master --user "'"$(valeur KEYCLOAK_ADMIN)"'" --password "$KC_MDP"' \
    >/dev/null 2>&1 && break
  [ "$i" = 60 ] && { echo "❌ Keycloak injoignable"; exit 1; }
  sleep 5
done

kc get "realms/$R" >/dev/null 2>&1 || { kc create realms -s realm=$R -s enabled=true >/dev/null; echo "✓ royaume $R créé"; }
kc update "realms/$R" -s displayName=VinaSign -s loginWithEmailAllowed=true -s duplicateEmailsAllowed=false \
  -s registrationAllowed=false -s resetPasswordAllowed=false -s rememberMe=false -s sslRequired=external \
  -s bruteForceProtected=true -s failureFactor=5 -s permanentLockout=false -s maxFailureWaitSeconds=900 \
  -s 'passwordPolicy=length(12) and notUsername and notEmail' -s internationalizationEnabled=true \
  -s 'supportedLocales=["fr","en"]' -s defaultLocale=fr -s accessTokenLifespan=300 -s ssoSessionIdleTimeout=1800
kc update "authentication/required-actions/CONFIGURE_TOTP" -r $R -s enabled=true -s defaultAction=true

# Parcours de connexion : copie du parcours « browser », références AMR sur mot de passe (pwd) et code (otp).
FLUX=navigateur-vinasign
kc get "authentication/flows" -r $R --fields alias 2>/dev/null | grep -q "\"$FLUX\"" \
  || kc create "authentication/flows/browser/copy" -r $R -s newName=$FLUX >/dev/null
reference() { # $1 fournisseur, $2 alias, $3 valeur amr
  local ex; ex="$(kc get "authentication/flows/$FLUX/executions" -r $R | python3 -c "
import json,sys
for e in json.load(sys.stdin):
    if e.get('providerId') == '$1': print(e['id'], e.get('authenticationConfig') or ''); break")"
  local id="${ex%% *}" conf="${ex#* }"
  local reglages=(-s alias="$2" -s "config.\"default.reference.value\"=$3" -s 'config."default.reference.maxAge"=36000')
  if [ -n "$conf" ] && [ "$conf" != "$id" ]; then kc update "authentication/config/$conf" -r $R "${reglages[@]}"
  else kc create "authentication/executions/$id/config" -r $R "${reglages[@]}" >/dev/null; fi
}
reference auth-username-password-form amr-pwd pwd
reference auth-otp-form amr-otp otp
kc update "realms/$R" -s browserFlow=$FLUX

# Client du portail : public, code d'autorisation avec PKCE S256, aucune autre méthode.
if ! kc get clients -r $R -q clientId=vinasign-portail --fields id | grep -q '"id"'; then
  kc create clients -r $R -s clientId=vinasign-portail -s name="Portail VinaSign" -s publicClient=true >/dev/null
fi
CLIENT="$(kc get clients -r $R -q clientId=vinasign-portail --fields id | python3 -c 'import json,sys; print(json.load(sys.stdin)[0]["id"])')"
kc update "clients/$CLIENT" -r $R -s publicClient=true -s standardFlowEnabled=true -s implicitFlowEnabled=false \
  -s directAccessGrantsEnabled=false -s serviceAccountsEnabled=false -s frontchannelLogout=true \
  -s "redirectUris=[\"$PORTAIL/connexion/retour\",\"$PORTAIL/connexion-silencieuse.html\"]" -s "webOrigins=[\"$PORTAIL\"]" \
  -s 'attributes."pkce.code.challenge.method"=S256' -s "attributes.\"post.logout.redirect.uris\"=$PORTAIL/"
mappeur() { # $1 nom, puis réglages
  kc get "clients/$CLIENT/protocol-mappers/models" -r $R --fields name | grep -q "\"$1\"" && return 0
  kc create "clients/$CLIENT/protocol-mappers/models" -r $R -s name="$1" -s protocol=openid-connect "${@:2}" >/dev/null
}
mappeur amr -s protocolMapper=oidc-amr-mapper -s 'config."access.token.claim"=true' -s 'config."id.token.claim"=true' \
  -s 'config."introspection.token.claim"=true'
mappeur audience-api -s protocolMapper=oidc-audience-mapper -s 'config."included.custom.audience"=api://vinasign' \
  -s 'config."access.token.claim"=true' -s 'config."id.token.claim"=false'
echo "✓ Keycloak : royaume $R, parcours AMR, client vinasign-portail"
