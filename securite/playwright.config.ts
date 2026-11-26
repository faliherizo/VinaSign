import { defineConfig } from '@playwright/test'

/** Tests d'intrusion contre le serveur réel (SECURITE_API_URL). Un seul ouvrier : les essais partagent l'état du serveur. */
export default defineConfig({
  testDir: './tests',
  timeout: 600_000,
  workers: 1,
  reporter: [['list'], ['json', { outputFile: 'resultats/playwright.json' }], ['html', { open: 'never' }]],
  use: {
    baseURL: process.env.SECURITE_API_URL ?? `http://localhost:${process.env.VINASIGN_PORT ?? 8300}`,
    extraHTTPHeaders: { Accept: 'application/json' },
  },
})
