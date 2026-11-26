#!/usr/bin/env node
// Cliquet de qualité (VS-002) : la construction échoue si une mesure baisse sous son plancher, même au-dessus du seuil
// réglementaire. `--relever` remonte les planchers aux valeurs mesurées ; il ne les abaisse jamais.
//
//   node qualite/cliquet.mjs              vérifie
//   node qualite/cliquet.mjs --relever    vérifie, puis relève les planchers atteints
import { readFileSync, writeFileSync, existsSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const ICI = dirname(fileURLToPath(import.meta.url));
const RACINE = join(ICI, '..');

/** Couverture agrégée de lignes et de branches, tirée du CSV de JaCoCo. */
export function mesurerCouverture(csv) {
  const [entete, ...lignes] = csv.trim().split(/\r?\n/);
  const col = Object.fromEntries(entete.split(',').map((nom, i) => [nom, i]));
  let lc = 0, lm = 0, bc = 0, bm = 0;
  for (const l of lignes) {
    const v = l.split(',');
    lc += Number(v[col.LINE_COVERED]); lm += Number(v[col.LINE_MISSED]);
    bc += Number(v[col.BRANCH_COVERED]); bm += Number(v[col.BRANCH_MISSED]);
  }
  return { lignes: ratio(lc, lm), branches: ratio(bc, bm) };
}

/** Score de mutation (MSI) : mutants tués ou en dépassement de délai sur l'ensemble des mutants. */
export function mesurerMutation(xml) {
  const statuts = [...xml.matchAll(/status=['"]([A-Z_]+)['"]/g)].map((m) => m[1]);
  const tues = statuts.filter((s) => s === 'KILLED' || s === 'TIMED_OUT').length;
  return { mutation: ratio(tues, statuts.length - tues) };
}

/** Compare les mesures aux planchers ; rend la liste des baisses, chacune nommée avec ses deux valeurs. */
export function comparer(mesures, planchers) {
  return Object.entries(mesures)
    .filter(([cle, valeur]) => planchers[cle] !== undefined && valeur + 1e-9 < planchers[cle])
    .map(([cle, valeur]) => `${cle} : ${pourcent(valeur)} mesuré, plancher ${pourcent(planchers[cle])}`);
}

/** Nouveaux planchers : chaque mesure supérieure remplace son plancher ; aucun plancher ne descend. */
export function relever(mesures, planchers) {
  const suivant = { ...planchers };
  for (const [cle, valeur] of Object.entries(mesures)) {
    const arrondi = Math.floor(valeur * 10000) / 10000;
    if (suivant[cle] === undefined || arrondi > suivant[cle]) suivant[cle] = arrondi;
  }
  return suivant;
}

function ratio(bon, mauvais) {
  const total = bon + mauvais;
  return total === 0 ? 1 : bon / total;
}

function pourcent(x) {
  return `${(x * 100).toFixed(2)} %`;
}

/** Exécute le cliquet sur les fichiers désignés ; rend le code de sortie (0 conforme, 1 baisse, 2 rapport absent). */
export function executer({ planchers: fichierPlanchers, csv, pit, doitRelever = false, journal = console }) {
  if (!existsSync(csv)) {
    journal.error(`Rapport JaCoCo absent : ${csv}. Lancer mvn verify d'abord.`);
    return 2;
  }
  const planchers = JSON.parse(readFileSync(fichierPlanchers, 'utf8'));
  const mesures = { ...mesurerCouverture(readFileSync(csv, 'utf8')) };
  if (existsSync(pit)) Object.assign(mesures, mesurerMutation(readFileSync(pit, 'utf8')));
  for (const [cle, valeur] of Object.entries(mesures)) journal.log(`${cle.padEnd(9)} ${pourcent(valeur)}`);
  const baisses = comparer(mesures, planchers);
  if (baisses.length > 0) {
    journal.error(`Baisse sous le cliquet :\n  ${baisses.join('\n  ')}`);
    return 1;
  }
  if (doitRelever) {
    writeFileSync(fichierPlanchers, `${JSON.stringify(relever(mesures, planchers), null, 2)}\n`);
    journal.log('Planchers relevés.');
  }
  return 0;
}

// point d'entrée de la ligne de commande : il ne fait que câbler les chemins du dépôt.
/* node:coverage disable */
if (process.argv[1] === fileURLToPath(import.meta.url)) {
  process.exit(executer({
    planchers: join(ICI, 'planchers.json'),
    csv: join(RACINE, 'serveur/target/site/jacoco/jacoco.csv'),
    pit: join(RACINE, 'serveur/target/pit-reports/mutations.xml'),
    doitRelever: process.argv.includes('--relever'),
  }));
}
/* node:coverage enable */
