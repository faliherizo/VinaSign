import { test } from 'node:test';
import assert from 'node:assert/strict';
import { mkdtempSync, readFileSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { comparer, executer, mesurerCouverture, mesurerMutation, relever } from './cliquet.mjs';

const CSV = `GROUP,PACKAGE,CLASS,INSTRUCTION_MISSED,INSTRUCTION_COVERED,BRANCH_MISSED,BRANCH_COVERED,LINE_MISSED,LINE_COVERED
g,p,A,0,10,1,3,1,9
g,p,B,0,10,0,4,0,10`;

test('agrège lignes et branches de toutes les classes', () => {
  assert.deepEqual(mesurerCouverture(CSV), { lignes: 19 / 20, branches: 7 / 8 });
});

test('le MSI compte les mutants tués et en dépassement de délai', () => {
  const xml = `<m status='KILLED'/><m status='TIMED_OUT'/><m status='SURVIVED'/><m status="NO_COVERAGE"/>`;
  assert.deepEqual(mesurerMutation(xml), { mutation: 0.5 });
});

test('sans mutant, le score est plein plutôt qu\'une division par zéro', () => {
  assert.deepEqual(mesurerMutation('<mutations/>'), { mutation: 1 });
});

test('une baisse est nommée avec la mesure et le plancher', () => {
  assert.deepEqual(comparer({ lignes: 0.95, branches: 0.99 }, { lignes: 0.97, branches: 0.99 }),
    ['lignes : 95.00 % mesuré, plancher 97.00 %']);
});

test('une mesure égale au plancher ou sans plancher passe', () => {
  assert.deepEqual(comparer({ lignes: 0.97, mutation: 0.5 }, { lignes: 0.97 }), []);
});

test('relever ne fait jamais descendre un plancher', () => {
  assert.deepEqual(relever({ lignes: 0.98765, branches: 0.9 }, { lignes: 0.97, branches: 0.95 }),
    { lignes: 0.9876, branches: 0.95 });
});

test('relever crée le plancher d\'une mesure nouvelle', () => {
  assert.deepEqual(relever({ mutation: 0.82 }, {}), { mutation: 0.82 });
});

function espace(planchers, { csv = CSV, pit } = {}) {
  const dir = mkdtempSync(join(tmpdir(), 'cliquet-'));
  const f = { planchers: join(dir, 'p.json'), csv: join(dir, 'j.csv'), pit: join(dir, 'm.xml') };
  writeFileSync(f.planchers, JSON.stringify(planchers));
  if (csv) writeFileSync(f.csv, csv);
  if (pit) writeFileSync(f.pit, pit);
  const messages = [];
  const journal = { log: (m) => messages.push(m), error: (m) => messages.push(m) };
  return { ...f, journal, messages };
}

test('sans rapport JaCoCo, la porte rend 2 et dit quoi lancer', () => {
  const e = espace({}, { csv: null });
  assert.equal(executer(e), 2);
  assert.match(e.messages[0], /mvn verify/);
});

test('une baisse rend 1 et laisse les planchers intacts', () => {
  const e = espace({ lignes: 0.99 });
  assert.equal(executer({ ...e, doitRelever: true }), 1);
  assert.deepEqual(JSON.parse(readFileSync(e.planchers, 'utf8')), { lignes: 0.99 });
  assert.match(e.messages.at(-1), /lignes : 95.00 % mesuré/);
});

test('conforme sans --relever : rend 0 et ne réécrit rien', () => {
  const e = espace({ lignes: 0.9 });
  assert.equal(executer(e), 0);
  assert.deepEqual(JSON.parse(readFileSync(e.planchers, 'utf8')), { lignes: 0.9 });
});

test('conforme avec --relever : planchers remontés, mutation comprise', () => {
  const e = espace({ lignes: 0.9 }, { pit: "<m status='KILLED'/>" });
  assert.equal(executer({ ...e, doitRelever: true }), 0);
  assert.deepEqual(JSON.parse(readFileSync(e.planchers, 'utf8')), { lignes: 0.95, branches: 0.875, mutation: 1 });
});
