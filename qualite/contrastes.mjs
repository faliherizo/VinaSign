/*
 * Calcul des rapports de contraste WCAG 2.x entre jetons de couleur (#rrggbb) du système de design.
 * Thème clair : premier bloc « :root » ; thème sombre : ce bloc, redéfini sous « prefers-color-scheme: dark ».
 */

function couleurs(bloc) {
  const jetons = {};
  for (const [, nom, valeur] of bloc.matchAll(/--vs-([a-z0-9-]+)\s*:\s*(#[0-9a-f]{6})\s*;/gi)) {
    jetons[nom] = valeur.toLowerCase();
  }
  return jetons;
}

export function lireThemes(css) {
  const sansCommentaires = css.replace(/\/\*[\s\S]*?\*\//g, '');
  const sombre = sansCommentaires.indexOf('@media (prefers-color-scheme: dark)');
  if (sombre < 0) throw new Error('Thème sombre absent : bloc « @media (prefers-color-scheme: dark) » attendu');
  const clair = couleurs(sansCommentaires.slice(0, sombre));
  const redefinis = couleurs(sansCommentaires.slice(sombre));
  return { clair, sombre: { ...clair, ...redefinis }, redefinis };
}

function canal(octet) {
  const c = octet / 255;
  return c <= 0.04045 ? c / 12.92 : ((c + 0.055) / 1.055) ** 2.4;
}

export function luminance(hex) {
  const [r, v, b] = [1, 3, 5].map((i) => canal(parseInt(hex.slice(i, i + 2), 16)));
  return 0.2126 * r + 0.7152 * v + 0.0722 * b;
}

export function contraste(a, b) {
  const [claire, sombre] = [luminance(a), luminance(b)].sort((x, y) => y - x);
  return (claire + 0.05) / (sombre + 0.05);
}

/** Rapport d'une paire dans un thème ; un jeton absent est une non-conformité nommée, pas une exception. */
export function rapport(jetons, avant, fond, seuil) {
  const a = jetons[avant];
  const f = jetons[fond];
  if (!a || !f) {
    return { conforme: false, libelle: `${avant} / ${fond} : jeton absent` };
  }
  const r = contraste(a, f);
  return { conforme: r >= seuil, ratio: r, libelle: `${avant} (${a}) / ${fond} (${f}) : ${r.toFixed(2)}:1 < ${seuil}:1` };
}
