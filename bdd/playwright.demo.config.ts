import { defineConfig, devices } from '@playwright/test'

/** Démonstration filmée (demo/) : 1280 × 720, enregistrement continu, contre la plateforme démarrée comme pour la BDD. */
const PORTAIL = process.env.BDD_PORTAIL_URL ?? `http://localhost:${process.env.VINASIGN_PORTAIL_PORT ?? 4300}`
const SIGNATAIRE = process.env.BDD_SIGNATAIRE_URL ?? `http://localhost:${process.env.VINASIGN_SIGNATAIRE_PORT ?? 4301}`

export default defineConfig({
  testDir: 'demo',
  workers: 1,
  timeout: 900_000,
  expect: { timeout: 20_000 },
  reporter: [['list']],
  outputDir: 'test-results-demo/brut',
  use: { baseURL: PORTAIL, locale: 'fr-CA', viewport: { width: 1280, height: 720 }, video: { mode: 'on', size: { width: 1280, height: 720 } } },
  projects: [{ name: 'chromium', use: { ...devices['Desktop Chrome'], locale: 'fr-CA', viewport: { width: 1280, height: 720 } } }],
  webServer: [
    { command: 'npx ng serve portail', cwd: '../web', url: PORTAIL, reuseExistingServer: true, timeout: 180_000 },
    { command: 'npx ng serve signataire', cwd: '../web', url: SIGNATAIRE, reuseExistingServer: true, timeout: 180_000 },
  ],
})
