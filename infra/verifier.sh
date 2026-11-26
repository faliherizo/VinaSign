#!/usr/bin/env bash
# Vérifie l'infrastructure en code sans compte infonuagique (VS-003) : format et validation Terraform, garde-fous
# éprouvés par terraform test (fournisseurs simulés), lint et rendu Helm, durcissement des conteneurs.
set -euo pipefail
ICI=$(cd "$(dirname "$0")" && pwd)

echo "== Terraform"
(cd "$ICI/terraform" && terraform fmt -check -recursive && terraform init -backend=false -input=false > /dev/null \
  && terraform validate && terraform test)

echo "== Helm"
CHART="$ICI/helm/vinasign"
helm lint "$CHART" -f "$CHART/values-exemple.yaml"
RENDU=$(helm template vs "$CHART" -f "$CHART/values-exemple.yaml" -n vinasign)
conteneurs=$(grep -cE '^ +image: ' <<< "$RENDU" || true)
durcis=$(grep -c 'readOnlyRootFilesystem: true' <<< "$RENDU" || true)
[[ "$conteneurs" -gt 0 && "$conteneurs" == "$durcis" ]] \
  || { echo "Chaque conteneur doit avoir une racine en lecture seule ($durcis sur $conteneurs)." >&2; exit 1; }
if helm template vs "$CHART" -n vinasign > /dev/null 2>&1; then
  echo "Le chart doit refuser de se rendre sans les valeurs propres à l'environnement." >&2; exit 1
fi
echo "Infrastructure conforme."
