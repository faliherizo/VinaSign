import { defineConfig, devices } from '@playwright/test'

/** Mêmes adresses que bdd/ : tirées de l'environnement ; le portail est démarré au besoin. */
const PORTAIL = process.env.E2E_PORTAIL_URL ?? `http://localhost:${process.env.VINASIGN_PORTAIL_PORT ?? 4300}`

export default defineConfig({
  testDir: './tests',
  workers: 1,
  reporter: [['list'], ['html', { open: 'never' }]],
  use: { baseURL: PORTAIL, locale: 'fr-CA', trace: 'retain-on-failure' },
  projects: [{ name: 'chromium', use: { ...devices['Desktop Chrome'], locale: 'fr-CA' } }],
  webServer: { command: 'npx ng serve portail', cwd: '../web', url: PORTAIL, reuseExistingServer: true, timeout: 180_000 },
})
