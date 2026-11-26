#!/usr/bin/env bash
# Prépare le VPS pour VinaSign, une seule fois (idempotent) : secrets tirés au hasard ici même, jamais transmis ni affichés.
#   - .env.vinasign (600) depuis env.vinasign.exemple ; chaque secret vide reçoit une valeur aléatoire ;
#   - secrets/jetons.pem : clé RSA (PKCS#8) de signature des jetons des applications clientes ;
#   - secrets/courriel.htpasswd : accès à la boîte des courriels de démonstration (bcrypt) ; son mot de passe est rangé
#     dans secrets/comptes-demo.txt (600), lisible du seul compte deploy.
set -euo pipefail
cd "$(dirname "$0")"
umask 077
mkdir -p secrets
alea() { openssl rand -base64 "${1:-32}" | tr -d '\n'; }
mdp() { openssl rand -base64 24 | tr -d '\n/+=' | cut -c1-24; }

if [ ! -f .env.vinasign ]; then
  cp env.vinasign.exemple .env.vinasign
  echo "✓ .env.vinasign créé"
fi
remplir() { # $1 clé, $2 valeur — seulement si la clé est vide
  grep -qE "^$1=.+" .env.vinasign && return 0
  local v; v="$2"
  python3 - "$1" "$v" <<'PY'
import sys
cle, val = sys.argv[1], sys.argv[2]
lignes = open('.env.vinasign').read().splitlines()
lignes = [f"{cle}={val}" if l.startswith(cle + '=') else l for l in lignes]
open('.env.vinasign', 'w').write('\n'.join(lignes) + '\n')
PY
  echo "✓ $1 généré"
}
remplir VINASIGN_DB_MDP "$(mdp)"
remplir VINASIGN_DB_MDP_APPLICATIF "$(mdp)"
remplir VINASIGN_AZURITE_CLE "$(alea 64)"
remplir VINASIGN_CLE_CODES "$(alea 32)"
remplir VINASIGN_CLE_CHIFFREMENT "$(alea 32)"
remplir KEYCLOAK_DB_MDP "$(mdp)"
remplir KEYCLOAK_ADMIN_MDP "$(mdp)"
chmod 600 .env.vinasign

[ -f secrets/jetons.pem ] || { openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:3072 -out secrets/jetons.pem 2>/dev/null; echo "✓ clé des jetons créée"; }

if [ ! -f secrets/courriel.htpasswd ]; then
  m="$(mdp)"
  docker run --rm httpd:2.4-alpine htpasswd -nbB demo "$m" > secrets/courriel.htpasswd
  printf 'Boîte des courriels (https://courriel.vinasign.vinaitsolution.com) : demo / %s\n' "$m" >> secrets/comptes-demo.txt
  echo "✓ accès à la boîte des courriels créé (secrets/comptes-demo.txt)"
fi
# Le conteneur Mailpit lit le fichier sous un autre compte : lecture seule pour tous, le dossier secrets reste fermé (700).
chmod 644 secrets/courriel.htpasswd
chmod 600 secrets/jetons.pem secrets/comptes-demo.txt 2>/dev/null || true
chmod 711 secrets
echo "✓ serveur prêt pour VinaSign"
