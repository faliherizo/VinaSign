// Rapport des tests d'intrusion (docs/securite/rapport-tests-intrusion.md), produit à partir des résultats réels.
// Rien n'y est écrit à la main : chaque test déclare sa catégorie OWASP dans son titre (« INT-xx Nom [A01 · A07] »)
// et consigne lui-même son volume et son libellé (resultats/*.json). Sortie non nulle si un test échoue ou si une
// vulnérabilité HAUTE/CRITIQUE n'est ni corrigée ni acceptée avec justification (donnees/acceptations.json).
import { existsSync, mkdirSync, readFileSync, readdirSync, writeFileSync } from 'node:fs'
import { execSync } from 'node:child_process'

const ici = new URL('../', import.meta.url)
const lire = (chemin, defaut = null) => (existsSync(new URL(chemin, ici)) ? JSON.parse(readFileSync(new URL(chemin, ici), 'utf8')) : defaut)
const constats = lire('donnees/constats.json', { constats: [] }).constats
const acceptations = lire('donnees/acceptations.json', { zap: [], trivy: [] })
const publique = lire('donnees/surface-publique.json', { operations: [] })
const pw = lire('resultats/playwright.json', { suites: [] })
const version = (() => {
  try {
    const base = execSync('git rev-parse --short HEAD', { stdio: ['ignore', 'pipe', 'ignore'] }).toString().trim()
    return execSync('git status --porcelain').toString().trim() ? `${base} + modifications locales` : base
  } catch { return 'dépôt sans commit' }
})()

function tests() {
  const liste = []
  const parcourir = (s, groupe) => {
    for (const enfant of s.suites ?? []) parcourir(enfant, /^INT-\d+/.test(enfant.title) ? enfant.title : groupe)
    for (const spec of s.specs ?? []) {
      const r = spec.tests.flatMap((t) => t.results)
      const statut = r.some((x) => x.status === 'passed') ? 'réussi' : r.some((x) => x.status === 'skipped') ? 'non exécuté' : 'ÉCHEC'
      liste.push({ groupe, titre: spec.title, statut })
    }
  }
  parcourir(pw, '')
  return liste
}

function zap() {
  const z = lire('resultats/zap.json')
  if (!z) return null
  return (z.site ?? []).flatMap((s) => s.alerts ?? []).map((a) => ({
    id: a.pluginid, nom: a.name, risque: a.riskdesc, instances: (a.instances ?? []).length,
    acceptation: acceptations.zap.find((x) => String(x.id) === String(a.pluginid)),
  }))
}

function trivy() {
  const t = lire('resultats/trivy.json')
  if (!t) return null
  const accepte = (id) => acceptations.trivy.find((x) => x.id === id)
  const res = t.Results ?? []
  return {
    vulns: res.flatMap((r) => (r.Vulnerabilities ?? []).map((v) => ({ ...v, cible: r.Target, acceptation: accepte(v.VulnerabilityID) }))),
    secrets: res.flatMap((r) => (r.Secrets ?? []).map((s) => ({ ...s, cible: r.Target, acceptation: accepte(s.RuleID) }))),
    mauvais: res.flatMap((r) => (r.Misconfigurations ?? []).map((m) => ({ ...m, cible: r.Target, acceptation: accepte(m.ID) }))),
  }
}

const t = tests()
const z = zap()
const tv = trivy()
const npm = lire('resultats/npm-audit.json')
const echecs = t.filter((x) => x.statut === 'ÉCHEC')
const zapGraves = (z ?? []).filter((a) => /High/i.test(a.risque) && !a.acceptation)
const graves = (tv?.vulns ?? []).filter((v) => ['HIGH', 'CRITICAL'].includes(v.Severity) && !v.acceptation)
const secretsNonAcceptes = (tv?.secrets ?? []).filter((s) => !s.acceptation)
const mauvaisGraves = (tv?.mauvais ?? []).filter((m) => ['HIGH', 'CRITICAL'].includes(m.Severity) && !m.acceptation)
const sain = echecs.length === 0 && zapGraves.length === 0 && graves.length === 0 && secretsNonAcceptes.length === 0 && mauvaisGraves.length === 0
const categorie = (groupe) => groupe.match(/\[([^\]]+)\]/)?.[1] ?? ''
const nomCourt = (groupe) => groupe.replace(/\s*\[[^\]]+\]/, '')

const l = ['# Rapport des tests d\'intrusion — VinaSign', '',
  `- **Version éprouvée** : ${version}`, `- **Date** : ${new Date().toISOString().slice(0, 10)}`,
  `- **Verdict** : ${sain ? '✅ aucun constat bloquant' : '❌ constats bloquants'}`, '',
  '## 1. Surface publique (sans authentification)', '', '| Opérations | Pourquoi publiques | Protection |', '|---|---|---|']
for (const p of publique.operations) l.push(`| \`${p.motif}\` | ${p.raison} | ${p.protection} |`)
l.push('', '## 2. Tests d\'intrusion automatisés', '', '| Test | OWASP | Scénario | Résultat |', '|---|---|---|---|')
for (const x of t) l.push(`| ${nomCourt(x.groupe)} | ${categorie(x.groupe)} | ${x.titre} | ${x.statut === 'réussi' ? '✅ réussi' : `❌ ${x.statut}`} |`)
const volumes = existsSync(new URL('resultats/', ici))
  ? readdirSync(new URL('resultats/', ici)).filter((f) => f.endsWith('.json')).map((f) => lire(`resultats/${f}`)).filter((d) => d?.libelle && d.volume !== undefined)
  : []
if (volumes.length) { l.push('', 'Volume éprouvé :', ''); for (const d of volumes) l.push(`- ${d.volume} ${d.libelle}`) }
l.push('', '## 3. Constats et corrections', '')
if (!constats.length) l.push('Aucun constat.')
else { l.push('| N° | Gravité | OWASP | Constat | Correction | Verrou | État |', '|---|---|---|---|---|---|---|'); for (const c of constats) l.push(`| ${c.id} | ${c.gravite} | ${c.owasp} | ${c.constat} | ${c.correction} | ${c.test} | ${c.etat} |`) }
l.push('', '## 4. Balayage dynamique OWASP ZAP', '')
if (!z) l.push('_Non exécuté._')
else { l.push('| Alerte | Risque | Instances | Décision |', '|---|---|---|---|'); for (const a of z) l.push(`| ${a.nom} (${a.id}) | ${a.risque} | ${a.instances} | ${a.acceptation ? `acceptée : ${a.acceptation.justification}` : (/High/i.test(a.risque) ? '**à corriger**' : 'information')} |`) }
l.push('', '## 5. Dépendances, secrets et configuration (Trivy, npm audit)', '')
if (!tv) l.push('_Trivy non exécuté._')
else {
  const n = (s) => tv.vulns.filter((v) => v.Severity === s).length
  l.push(`Vulnérabilités : ${n('CRITICAL')} critique(s), ${n('HIGH')} haute(s), ${n('MEDIUM')} moyenne(s), ${n('LOW')} faible(s). Secrets : ${tv.secrets.length}. Défauts de configuration graves : ${mauvaisGraves.length}.`)
  for (const v of graves) l.push(`- **${v.VulnerabilityID}** ${v.PkgName} ${v.InstalledVersion} (${v.cible}) → ${v.FixedVersion ?? 'pas de correctif'}`)
  for (const m of mauvaisGraves) l.push(`- **${m.ID}** ${m.Title} (${m.cible})`)
}
if (npm?.metadata?.vulnerabilities) { const v = npm.metadata.vulnerabilities; l.push('', `npm audit (production) : ${v.critical} critique(s), ${v.high} haute(s), ${v.moderate} moyenne(s), ${v.low} faible(s).`) }
l.push('', '## 6. Conditions d\'exploitation', '')
for (const c of lire('donnees/conditions.json', { conditions: [] }).conditions) l.push(`- ${c}`)
mkdirSync(new URL('../docs/securite/', ici), { recursive: true })
writeFileSync(new URL('../docs/securite/rapport-tests-intrusion.md', ici), l.join('\n') + '\n')
console.log(`Rapport écrit : ${t.length} tests (${echecs.length} échec), ZAP élevées non traitées ${zapGraves.length}, dépendances graves ${graves.length}, secrets ${secretsNonAcceptes.length}.`)
process.exit(sain ? 0 : 1)
