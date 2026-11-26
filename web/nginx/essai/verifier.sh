#!/usr/bin/env bash
# Essai du frontal nginx (VS-055, VS-050) dans l'image réelle : seule une session intégrée est intégrable, par les
# origines que le serveur donne pour son jeton ; le jeton part en en-tête, jamais dans l'URL relayée ; le module
# PDF.js est servi en JavaScript. Prérequis : Docker, python3, curl.
set -euo pipefail
ICI="$(cd "$(dirname "$0")" && pwd)"
PORT_AMONT="${ESSAI_PORT_AMONT:-28777}"
PORT_FRONTAL="${ESSAI_PORT_FRONTAL:-28778}"
TRAVAIL="$(mktemp -d)"
mkdir -p "$TRAVAIL/html" && echo '<html></html>' > "$TRAVAIL/html/index.html" && echo '' > "$TRAVAIL/html/pdf.worker.min.mjs"
python3 "$ICI/amont.py" "$PORT_AMONT" & AMONT=$!
FRONTAL=$(docker run -d -p "$PORT_FRONTAL:8080" -e "VINASIGN_API_URL=http://host.docker.internal:$PORT_AMONT" \
  -e "VINASIGN_ANCETRES_AUTORISES='none'" -e "VINASIGN_FOURNISSEUR_IDENTITE=https://idp.exemple.ca" --add-host host.docker.internal:host-gateway \
  -v "$ICI/../default.conf.template:/etc/nginx/templates/default.conf.template:ro" -v "$TRAVAIL/html:/usr/share/nginx/html:ro" \
  nginxinc/nginx-unprivileged:1.27-alpine)
trap 'docker rm -f "$FRONTAL" >/dev/null; kill "$AMONT"; wait "$AMONT" 2>/dev/null; rm -rf "$TRAVAIL"' EXIT
for _ in $(seq 1 30); do curl -sf -o /dev/null "localhost:$PORT_FRONTAL/" && break; sleep 1; done

echecs=0
attendre() { # chemin, motif attendu dans les en-têtes
  if curl -s -D - -o /dev/null "localhost:$PORT_FRONTAL$1" | grep -qiE "$2"; then echo "ok    $1 : $2"; else echo "ÉCHEC $1 : $2"; echecs=$((echecs + 1)); fi
}
attendre /integre/JETON_VALIDE_0123456789 "frame-ancestors https://rh\.exemple\.ca\"?\s*$"
attendre /integre/JETON_VALIDE_0123456789 "^cache-control: no-store"
attendre /integre/AUTRE_JETON_0123456789 "frame-ancestors 'none'"
attendre /s/JETON_VALIDE_0123456789 "frame-ancestors 'none'"
attendre / "frame-ancestors 'none'"
attendre / "connect-src 'self' https://idp\.exemple\.ca; frame-src 'self' https://idp\.exemple\.ca;"
attendre /pdf.worker.min.mjs "^content-type: text/javascript"
[ "$echecs" -eq 0 ] && echo "Frontal conforme." || { echo "$echecs écart(s)."; exit 1; }
