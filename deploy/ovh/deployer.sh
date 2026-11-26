#!/usr/bin/env bash
# Déploie une version de VinaSign sur le VPS — appelé par .github/workflows/deployer-vinasign.yml, ou à la main.
#
#   deploy/ovh/deployer.sh <version>          # images vinasign/{serveur,web-portail,web-signataire,icp}:<version> chargées
#   deploy/ovh/deployer.sh --retour-arriere   # revient à la version précédente, base comprise
#
# 1. sauvegarde la base (pg_dump) — ~/sauvegardes/vinasign, les 10 dernières ;
# 2. démarre l'ICP, lit le mot de passe du magasin de cachet dans son volume et la clé des jetons dans secrets/ : ils ne
#    passent que par l'environnement du processus compose, jamais par un fichier ni par la ligne de commande ;
# 3. démarre la version (Liquibase applique ses migrations), attend la santé du serveur et des deux interfaces ;
# 4. configure Keycloak (idempotent) et crée l'administrateur de la plateforme s'il manque ;
# 5. en cas d'échec : restaure la base, redémarre la version précédente, sort en erreur.
set -euo pipefail
ICI="$(cd "$(dirname "$0")" && pwd)"
cd "$ICI"
SAUVEGARDES="$HOME/sauvegardes/vinasign"
DELAI_SANTE_S=420
[ -f .env.vinasign ] || { echo "❌ deploy/ovh/.env.vinasign absent — lancer deploy/ovh/preparer-serveur.sh"; exit 2; }
[ -f secrets/jetons.pem ] || { echo "❌ secrets/jetons.pem absent — lancer deploy/ovh/preparer-serveur.sh"; exit 2; }
PRECEDENTE="$(cat .version-courante 2>/dev/null || true)"
if [ "${1:-}" = "--retour-arriere" ]; then
  VERSION="$(cat .version-precedente 2>/dev/null || true)"; [ -n "$VERSION" ] || { echo "❌ aucune version précédente"; exit 2; }
else
  VERSION="${1:?version attendue (sha court)}"
fi
valeur() { grep -E "^$1=" .env.vinasign | cut -d= -f2-; }
UTILISATEUR_BASE="$(valeur VINASIGN_DB_UTILISATEUR)"
composer() {
  VINASIGN_VERSION="$1" VINASIGN_AZURITE_CLE="$(valeur VINASIGN_AZURITE_CLE)" docker compose -p vinasign --env-file .env.vinasign -f docker-compose.yml "${@:2}"
}
for image in serveur web-portail web-signataire icp; do
  docker image inspect "vinasign/$image:$VERSION" >/dev/null 2>&1 || { echo "❌ image vinasign/$image:$VERSION absente (docker load)"; exit 2; }
done

ARCHIVE=""
sauvegarder() {
  [ "$(docker inspect -f '{{.State.Running}}' vinasign-db 2>/dev/null)" = "true" ] || return 0
  mkdir -p "$SAUVEGARDES"; chmod 700 "$SAUVEGARDES"
  ARCHIVE="$SAUVEGARDES/vinasign-$(date +%Y%m%d-%H%M%S)-${PRECEDENTE:-initiale}.dump"
  docker exec vinasign-db pg_dump -U "$UTILISATEUR_BASE" -d vinasign -Fc > "$ARCHIVE"; chmod 600 "$ARCHIVE"
  ls -1t "$SAUVEGARDES"/vinasign-*.dump | tail -n +11 | xargs -r rm -f
  echo "✓ sauvegarde : $ARCHIVE ($(du -h "$ARCHIVE" | cut -f1))"
}
restaurer() {
  [ -n "$ARCHIVE" ] || return 0
  echo "↩ restauration de la base : $ARCHIVE"
  docker stop vinasign-serveur >/dev/null 2>&1 || true
  docker exec -i vinasign-db pg_restore -U "$UTILISATEUR_BASE" -d vinasign --clean --if-exists --no-owner < "$ARCHIVE"
}
sain() { [ "$(docker inspect -f '{{.State.Health.Status}}' "$1" 2>/dev/null)" = "healthy" ]; }
en_sante() {
  local fin=$((SECONDS + DELAI_SANTE_S))
  while [ $SECONDS -lt $fin ]; do
    if sain vinasign-serveur && docker exec vinasign-portail wget -qO /dev/null http://127.0.0.1:8080/api/v1/referentiels \
      && docker exec vinasign-signataire wget -qO /dev/null http://127.0.0.1:8080/; then return 0; fi
    sleep 5
  done
  return 1
}
demarrer() {
  # Compose interpole tout le fichier, serveur compris : ses deux secrets reçoivent ici une valeur d'attente, sans effet
  # sur les services démarrés dans cette première phase (le serveur n'en fait pas partie).
  VINASIGN_CACHET_MOT_DE_PASSE=attente VINASIGN_JETONS_CLE_PRIVEE=attente composer "$1" up -d base stockage conversion icp courriel keycloak
  local fin=$((SECONDS + 180)); until sain vinasign-icp; do [ $SECONDS -lt $fin ] || { echo "❌ ICP non prête"; return 1; }; sleep 3; done
  VINASIGN_CACHET_MOT_DE_PASSE="$(docker exec vinasign-icp cat /etat/mot-de-passe.txt)" \
  VINASIGN_JETONS_CLE_PRIVEE="$(cat secrets/jetons.pem)" composer "$1" up -d --remove-orphans
}

echo "▶ déploiement de VinaSign $VERSION (précédente : ${PRECEDENTE:-aucune})"
sauvegarder
demarrer "$VERSION"
if ! en_sante; then
  echo "❌ $VERSION ne répond pas après ${DELAI_SANTE_S} s — journal du serveur :"
  docker logs --tail 80 vinasign-serveur 2>&1 || true
  if [ -n "$PRECEDENTE" ] && [ "$PRECEDENTE" != "$VERSION" ]; then
    restaurer; echo "↩ retour à $PRECEDENTE"; demarrer "$PRECEDENTE"
    en_sante && echo "✓ $PRECEDENTE rétablie" || echo "❌ $PRECEDENTE ne répond pas non plus"
  fi
  exit 1
fi
"$ICI/keycloak/configurer.sh"
"$ICI/comptes-demo.sh"
if [ "$VERSION" != "$PRECEDENTE" ]; then
  [ -n "$PRECEDENTE" ] && echo "$PRECEDENTE" > .version-precedente
  echo "$VERSION" > .version-courante
fi
GARDER="$(cat .version-courante) $(cat .version-precedente 2>/dev/null || true)"
for ref in $(docker images --format '{{.Repository}}:{{.Tag}}' | grep '^vinasign/'); do
  [[ " $GARDER " == *" ${ref##*:} "* ]] || docker rmi "$ref" >/dev/null 2>&1 || true
done
echo "✓ VinaSign $VERSION en service — https://vinasign.vinaitsolution.com"
