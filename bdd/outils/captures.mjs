import { chromium } from '@playwright/test'
import { mkdirSync } from 'fs'
import { join } from 'path'

/*
 * Captures des écrans publics du portail et de l'écran du signataire, en thème clair et sombre, au téléphone et au
 * portable, pour la revue visuelle du système de design (docs/architecture/design-et-accessibilite.md).
 * Usage : node outils/captures.mjs [dossier] ; interfaces démarrées (ng serve portail et signataire).
 */
const PORTAIL = process.env.BDD_PORTAIL_URL ?? 'http://localhost:4300'
const SIGNATAIRE = process.env.BDD_SIGNATAIRE_URL ?? 'http://localhost:4301'
const DOSSIER = process.argv[2] ?? 'test-results/captures'
const ECRANS = [
  ['vitrine', `${PORTAIL}/`], ['etat', `${PORTAIL}/etat`], ['inscription', `${PORTAIL}/inscription`],
  ['connexion', `${PORTAIL}/connexion?retour=%2Ffacturation`], ['referentiels', `${PORTAIL}/referentiels`],
  ['signataire-accueil', `${SIGNATAIRE}/`],
]
const LARGEURS = [390, 1280]

mkdirSync(DOSSIER, { recursive: true })
const navigateur = await chromium.launch()
for (const theme of ['light', 'dark']) {
  for (const largeur of LARGEURS) {
    const page = await navigateur.newPage({ viewport: { width: largeur, height: 900 }, colorScheme: theme, locale: 'fr-CA' })
    for (const [nom, adresse] of ECRANS) {
      await page.goto(adresse)
      await page.locator('h1').first().waitFor()
      await page.waitForLoadState('networkidle')
      await page.screenshot({ path: join(DOSSIER, `${nom}-${largeur}-${theme}.png`), fullPage: true })
    }
    await page.close()
  }
}
await navigateur.close()
