#!/usr/bin/env bash
# Chaîne complète de VinaSign, dans l'ordre des portes. Sortie non nulle à la première porte franchie à tort.
# Prérequis : Docker ; .env (copie de .env.exemple) ; Chrome pour Karma (CHROME_BIN), installé par Playwright au besoin.
set -euo pipefail
RACINE=$(cd "$(dirname "$0")" && pwd)
cd "$RACINE"
[[ -f .env ]] || { echo "Créer .env à partir de .env.exemple." >&2; exit 2; }
set -a; . ./.env; set +a
JAVA21=$(/usr/libexec/java_home -v 21 2>/dev/null || echo "${JAVA_HOME:?Java 21 requis}")
CHROME_BIN="${CHROME_BIN:-$(find "$HOME/Library/Caches/ms-playwright" -path '*chrome-mac*' -name 'Google Chrome for Testing' -type f 2>/dev/null | sort | tail -1)}"
export CHROME_BIN

etape() { echo; echo "== $*"; }

etape "1. Serveur : tests, architecture, couverture par classe (≥ 96 %), SBOM, cliquet (aucune baisse)"
(cd serveur && JAVA_HOME="$JAVA21" mvn -q verify)
node --test 'qualite/*.test.mjs' > /dev/null
node qualite/cliquet.mjs

etape "2. Interfaces : tests et couverture par fichier, constructions de production"
(cd web && npm ci --silent && for p in commun portail signataire; do npx ng test "$p" --watch=false; done \
  && npx ng build portail && npx ng build signataire)
(cd sdk/ts/embed && npm test)
(cd sdk/ts/client && npm ci --silent && npm test)
# SDK Java (VS-092) : contrat Java 17, éprouvé sous 17 et 21 ; client généré depuis serveur/contrat/openapi-v1.json.
for v in 17 21; do (cd sdk/java && JAVA_HOME="$(/usr/libexec/java_home -v $v)" mvn -q verify); done
# SDK .NET 8 (VS-125) : client généré depuis le contrat, test de contrat, couverture.
(cd sdk/dotnet/VinaSign.Client.Tests && dotnet test)

etape "2b. Infrastructure en code (Terraform, Helm) et frontal nginx"
infra/verifier.sh
web/nginx/essai/verifier.sh

etape "3. Base de développement et serveur"
docker compose --env-file .env -f infra/docker-compose.dev.yml up -d --wait
# Fournisseur OIDC du portail simulé (bac à sable) : les scénarios d'administration s'y authentifient (VS-014).
docker compose -f infra/bacs-a-sable/docker-compose.yml up -d --wait oidc icp pyhanko conversion
OIDC="http://localhost:${VS_OIDC_PORT:-28094}/portail"
(cd serveur && JAVA_HOME="$JAVA21" mvn -q -DskipTests package)
# Toute la campagne (BDD, e2e, intrusion, charge) part d'une seule adresse : le débit anonyme par adresse (VS-015) est
# relevé pour ce serveur, sinon le plan de charge mesurerait la limitation au lieu de l'API. La limitation elle-même est
# éprouvée par DebitApiTest.
# Clés des codes et du chiffrement : celles du .env, sinon éphémères pour cette campagne (jamais écrites sur disque).
VINASIGN_CLE_CODES="${VINASIGN_CLE_CODES:-$(openssl rand -base64 32)}" VINASIGN_CLE_CHIFFREMENT="${VINASIGN_CLE_CHIFFREMENT:-$(openssl rand -base64 32)}" \
VINASIGN_SMS_FOURNISSEUR="${VINASIGN_SMS_FOURNISSEUR:-simulateur}" \
VINASIGN_DEBIT_ANONYME="${VINASIGN_DEBIT_ANONYME_CAMPAGNE:-1000000}" \
VINASIGN_PORTAIL_EMETTEUR="$OIDC" VINASIGN_PORTAIL_JWKS="$OIDC/jwks" VINASIGN_PORTAIL_AUDIENCE="api://vinasign-bdd" \
VINASIGN_STOCKAGE_CREER_CONTENEUR=true VINASIGN_COURRIELS_INTERVALLE=PT2S \
VINASIGN_CACHET_FOURNISSEUR=pkcs12 VINASIGN_CACHET_MAGASIN="$RACINE/infra/bacs-a-sable/icp/etat/cachet.p12" \
VINASIGN_CACHET_MOT_DE_PASSE="$(cat infra/bacs-a-sable/icp/etat/mot-de-passe.txt)" VINASIGN_ENVIRONNEMENT=bac-a-sable \
VINASIGN_HORODATAGE_URL="http://localhost:${VS_ICP_PORT:-28090}/tsa" VINASIGN_CONVERSION_URL="http://localhost:${VS_CONVERSION_PORT:-28030}" \
VINASIGN_DB_URL="jdbc:postgresql://localhost:${VINASIGN_DB_PORT}/${VINASIGN_DB_NOM}" \
  "$JAVA21/bin/java" -jar serveur/target/vinasign-serveur-*.jar > serveur/target/serveur.log 2>&1 &
SERVEUR=$!
trap 'kill $SERVEUR 2>/dev/null || true' EXIT
for _ in $(seq 1 60); do curl -sf "http://localhost:${VINASIGN_PORT}/actuator/health" > /dev/null && break; sleep 2; done

etape "4. BDD : couverture du catalogue, scénarios, documentation"
(cd bdd && npm ci --silent && npm run couverture && npx bddgen && npx playwright test; s=$?; node outils/documenter.mjs; exit $s)

etape "5. Bout en bout (accès base)"
(cd e2e && npm ci --silent && npx playwright test)

etape "6. Tests d'intrusion (ZAP et Trivy : securite/lancer.sh)"
(cd securite && npm ci --silent && npx playwright test && node outils/rapport.mjs)

etape "7. Performance (gate SLO)"
(cd perf && ./run.sh)

echo; echo "✅ Toutes les portes sont franchies."
