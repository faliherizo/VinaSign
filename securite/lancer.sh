#!/usr/bin/env bash
# Campagne complète contre un serveur en marche (SECURITE_API_URL ; défaut : port VINASIGN_PORT local) :
#  1. tests d'intrusion automatisés sur tout l'inventaire OpenAPI ;
#  2. OWASP ZAP (balayage de l'API à partir d'OpenAPI) ;
#  3. Trivy : dépendances, secrets, configuration (Dockerfile, Terraform, Helm) ;
#  4. npm audit des interfaces (dépendances de production) ;
#  5. rapport docs/securite/rapport-tests-intrusion.md.
# Sortie non nulle si un test échoue ou si une alerte HAUTE ou CRITIQUE n'est ni corrigée ni acceptée.
set -uo pipefail
ICI=$(cd "$(dirname "$0")" && pwd)
RACINE=$(cd "$ICI/.." && pwd)
R="$ICI/resultats"; mkdir -p "$R"
API="${SECURITE_API_URL:-http://localhost:${VINASIGN_PORT:-8300}}"
API_DOCKER="${API/localhost/host.docker.internal}"
ZAP_IMAGE="${ZAP_IMAGE:-zaproxy/zap-stable:2.16.1}"
TRIVY_IMAGE="${TRIVY_IMAGE:-aquasec/trivy:0.58.1}"
statut=0

echo "== 1. Tests d'intrusion automatisés"
[[ -d "$ICI/node_modules" ]] || (cd "$ICI" && npm ci)
(cd "$ICI" && SECURITE_API_URL="$API" npx playwright test) || statut=1

echo "== 2. OWASP ZAP"
docker run --rm -v "$R:/zap/wrk:rw" "$ZAP_IMAGE" zap-api-scan.py -t "$API_DOCKER/v3/api-docs" -f openapi \
  -J zap.json -r zap.html -I -z "-config scanner.maxScanDurationInMins=${ZAP_DUREE_MAX_MIN:-20}" > "$R/zap.log" 2>&1 || true

echo "== 3. Trivy"
docker run --rm -v "$RACINE:/src:ro" -v "$R:/sortie" -v trivy-cache:/root/.cache "$TRIVY_IMAGE" fs \
  --scanners vuln,secret,misconfig --format json -o /sortie/trivy.json \
  --skip-dirs '**/node_modules' --skip-dirs serveur/target --skip-dirs web/dist --skip-dirs '**/.terraform' \
  --skip-dirs infra/bacs-a-sable/icp/etat --skip-dirs securite/resultats /src > "$R/trivy.log" 2>&1 || true

echo "== 4. npm audit (interfaces, production)"
(cd "$RACINE/web" && npm audit --omit=dev --json > "$R/npm-audit.json" 2>/dev/null) || true

echo "== 5. Rapport"
(cd "$ICI" && node outils/rapport.mjs) || statut=1
exit $statut
