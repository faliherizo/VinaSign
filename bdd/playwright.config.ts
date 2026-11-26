import { defineConfig, devices } from '@playwright/test'
import { defineBddConfig } from 'playwright-bdd'

/**
 * Scénarios BDD (Gherkin français) exécutés de bout en bout : le portail relaie /api vers le serveur VinaSign. Les
 * adresses viennent de l'environnement ; les interfaces sont démarrées au besoin (réutilisées si elles tournent).
 */
const PORTAIL = process.env.BDD_PORTAIL_URL ?? `http://localhost:${process.env.VINASIGN_PORTAIL_PORT ?? 4300}`
const SIGNATAIRE = process.env.BDD_SIGNATAIRE_URL ?? `http://localhost:${process.env.VINASIGN_SIGNATAIRE_PORT ?? 4301}`

const testDir = defineBddConfig({
  features: 'features/**/*.feature',
  steps: ['etapes/**/*.ts'],
  language: 'fr',
})

export default defineConfig({
  testDir,
  fullyParallel: false,
  workers: 1,
  timeout: 120_000,
  expect: { timeout: 15_000 },
  reporter: [['list'], ['html', { open: 'never' }], ['json', { outputFile: 'test-results/resultats.json' }]],
  use: { baseURL: PORTAIL, locale: 'fr-CA', trace: 'retain-on-failure', screenshot: 'only-on-failure' },
  projects: [{ name: 'chromium', use: { ...devices['Desktop Chrome'], locale: 'fr-CA' } }],
  webServer: [
    { command: 'npx ng serve portail', cwd: '../web', url: PORTAIL, reuseExistingServer: true, timeout: 180_000 },
    { command: 'npx ng serve signataire', cwd: '../web', url: SIGNATAIRE, reuseExistingServer: true, timeout: 180_000 },
  ],
})
