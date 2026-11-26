import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync, readdirSync, statSync } from 'node:fs';
import { join } from 'node:path';
import { lireThemes, rapport } from './contrastes.mjs';

/*
 * Contrastes du système de design (WCAG 2.2 AA, SGQRI 008 3.0) : chaque paire de jetons réellement employée par les
 * interfaces est calculée dans le thème clair et le thème sombre. Texte ≥ 4,5:1 (1.4.3) ; limites de composants,
 * indicateurs de focus et repères graphiques ≥ 3:1 (1.4.11, 2.4.7).
 */
const RACINE = new URL('..', import.meta.url).pathname;
const JETONS = join(RACINE, 'web/projects/commun/styles/jetons.css');
const TEXTE = 4.5;
const GRAPHIQUE = 3;

const PAIRES = [
  ...['fond', 'surface', 'surface-alt', 'info-fond', 'erreur-fond', 'succes-fond', 'alerte-fond', 'primaire-doux']
    .map((f) => ['texte', f, TEXTE]),
  ...['fond', 'surface', 'surface-alt'].map((f) => ['texte-doux', f, TEXTE]),
  ...['fond', 'surface', 'surface-alt', 'info-fond'].map((f) => ['lien', f, TEXTE]),
  ...['fond', 'surface', 'surface-alt', 'primaire-doux', 'info-fond'].map((f) => ['primaire', f, TEXTE]),
  ['sur-primaire', 'primaire', TEXTE],
  ['sur-primaire', 'primaire-survol', TEXTE],
  ...['fond', 'surface', 'erreur-fond'].map((f) => ['erreur', f, TEXTE]),
  ...['fond', 'surface', 'succes-fond'].map((f) => ['succes', f, TEXTE]),
  ...['fond', 'surface', 'alerte-fond'].map((f) => ['alerte', f, TEXTE]),
  ['desactive-texte', 'desactive-fond', TEXTE],
  ['surface', 'erreur', TEXTE],
  ['succes-fond', 'succes', TEXTE],
  ...['heros-debut', 'heros-fin'].flatMap((f) => [['sur-heros', f, TEXTE], ['sur-heros-doux', f, TEXTE]]),
  ['heros-debut', 'sur-heros-doux', TEXTE],
  ['zone-texte', 'zone-fond', TEXTE],
  ...['fond', 'surface', 'surface-alt'].map((f) => ['bordure', f, GRAPHIQUE]),
  ...['fond', 'surface', 'surface-alt', 'info-fond', 'primaire-doux'].map((f) => ['focus', f, GRAPHIQUE]),
  ...['heros-debut', 'heros-fin'].map((f) => ['focus-inverse', f, GRAPHIQUE]),
  ...['document', 'zone-fond'].flatMap((f) => [['zone-trait', f, GRAPHIQUE], ['zone-choisie', f, GRAPHIQUE]]),
  ['desactive-texte', 'desactive-fond', GRAPHIQUE],
];

const themes = lireThemes(readFileSync(JETONS, 'utf8'));

for (const theme of ['clair', 'sombre']) {
  const jetons = themes[theme];
  test(`thème ${theme} : chaque paire de jetons atteint son seuil de contraste`, () => {
    const echecs = PAIRES.map(([avant, fond, seuil]) => rapport(jetons, avant, fond, seuil)).filter((r) => !r.conforme);
    assert.deepEqual(echecs.map((r) => r.libelle), []);
  });
}

test('le thème sombre redéfinit chaque couleur du thème clair (aucune ne retombe sur sa valeur claire)', () => {
  const manquantes = Object.keys(themes.clair).filter((j) => !(j in themes.redefinis) && !['document', 'zone-trait', 'zone-fond',
    'zone-texte', 'zone-choisie'].includes(j));
  assert.deepEqual(manquantes, []);
});

/** Feuilles des interfaces : une couleur écrite en dur échapperait au calcul ; elle doit passer par un jeton. */
function feuilles(dossier) {
  return readdirSync(dossier).flatMap((nom) => {
    const chemin = join(dossier, nom);
    if (statSync(chemin).isDirectory()) return feuilles(chemin);
    return nom.endsWith('.css') && chemin !== JETONS ? [chemin] : [];
  });
}

test('aucune feuille de style des interfaces n\'écrit de couleur hors des jetons', () => {
  const fautives = feuilles(join(RACINE, 'web/projects'))
    .filter((f) => /#[0-9a-f]{3,8}\b|rgba?\(|hsla?\(/i.test(readFileSync(f, 'utf8').replace(/\/\*[\s\S]*?\*\//g, '')))
    .map((f) => f.slice(RACINE.length));
  assert.deepEqual(fautives, []);
});

test('le calcul retrouve les rapports de référence de la WCAG', async () => {
  const { contraste } = await import('./contrastes.mjs');
  assert.equal(contraste('#000000', '#ffffff'), 21);
  assert.equal(contraste('#777777', '#777777'), 1);
  assert.ok(Math.abs(contraste('#767676', '#ffffff') - 4.54) < 0.01);
});

test('un jeton absent est une non-conformité nommée', () => {
  assert.deepEqual(rapport({ texte: '#000000' }, 'texte', 'fond', 4.5), { conforme: false, libelle: 'texte / fond : jeton absent' });
});
