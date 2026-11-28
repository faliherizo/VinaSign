#!/usr/bin/env bash
# Régénère les icônes matricielles à partir de favicon.svg (seule source) : favicon.ico (16, 32, 48 px) pour les
# navigateurs qui ignorent le SVG et apple-touch-icon.png (180 px) pour l'écran d'accueil iOS.
# Requiert rsvg-convert (librsvg) et Python 3 avec Pillow. À relancer après toute modification du SVG.
set -euo pipefail
ICI="$(cd "$(dirname "$0")" && pwd)"
TMP="$(mktemp -d)"; trap 'rm -rf "$TMP"' EXIT
for t in 16 32 48 180; do rsvg-convert -w "$t" -h "$t" "$ICI/favicon.svg" -o "$TMP/$t.png"; done
cp "$TMP/180.png" "$ICI/apple-touch-icon.png"
python3 -I - "$TMP" "$ICI/favicon.ico" <<'PY'
import sys
from PIL import Image
src, dest = sys.argv[1], sys.argv[2]
images = [Image.open(f"{src}/{t}.png").convert("RGBA") for t in (48, 32, 16)]
images[0].save(dest, format="ICO", sizes=[(48, 48), (32, 32), (16, 16)], append_images=images[1:])
PY
echo "✓ icônes régénérées dans $ICI"
