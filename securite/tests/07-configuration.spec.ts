import { expect, test } from '@playwright/test'
import { readFileSync } from 'node:fs'
import { anonyme, estPublique, inventaire, remplir } from '../outils/api'
import { consigner } from '../outils/consigner'

const RACINE = new URL('../../', import.meta.url)

/** Configuration : en-têtes de sécurité, CORS, erreurs, méthodes, frontal. */
test.describe('INT-07 Configuration de sécurité [A05]', () => {
  test('l\'API pose les en-têtes de sécurité sur ses réponses', async () => {
    const op = (await inventaire()).find((o) => o.methode === 'GET' && estPublique(o))
    expect(op, 'une opération publique en lecture').toBeDefined()
    const h = (await (await anonyme()).get(remplir(op!))).headers()
    const attendus: Record<string, RegExp> = {
      'x-content-type-options': /^nosniff$/,
      'x-frame-options': /^DENY$/i,
      'content-security-policy': /default-src 'none'/,
      'referrer-policy': /no-referrer/,
      'cache-control': /no-store|no-cache/,
      'cross-origin-resource-policy': /^same-origin$/,
      'cross-origin-opener-policy': /^same-origin$/,
    }
    const ecarts = Object.entries(attendus).filter(([n, v]) => !v.test(h[n] ?? '')).map(([n]) => `${n} : ${h[n] ?? 'absent'}`)
    consigner('07-entetes-api', { entetes: h, ecarts })
    expect(ecarts).toEqual([])
  })

  test('une origine étrangère n\'obtient pas l\'autorisation CORS', async () => {
    const op = (await inventaire()).find((o) => o.methode === 'GET' && estPublique(o))!
    const r = await (await anonyme()).fetch(remplir(op), { method: 'OPTIONS', failOnStatusCode: false,
      headers: { Origin: 'https://pirate.example', 'Access-Control-Request-Method': 'GET' } })
    expect(r.headers()['access-control-allow-origin']).toBeUndefined()
  })

  test('les erreurs ne divulguent aucune trace et TRACE est fermé', async () => {
    const anon = await anonyme()
    for (const url of ['/api/v1/route-inexistante', '/api/v1/referentiels/%00']) {
      const r = await anon.fetch(url, { failOnStatusCode: false })
      expect(r.status()).toBeLessThan(500)
      expect(await r.text()).not.toMatch(/Exception|at [a-z]+\.[a-z]+\./)
    }
    // Refusé par le pare-feu HTTP de Spring Security (400) avant tout routage : la requête n'est jamais renvoyée en écho.
    const trace = await anon.fetch('/api/v1/referentiels', { method: 'TRACE', failOnStatusCode: false, headers: { 'X-Sonde': 'echo' } })
    expect(trace.status()).toBeGreaterThanOrEqual(400)
    expect(await trace.text()).not.toContain('X-Sonde')
  })

  test('les frontaux posent CSP, HSTS et la protection contre l\'encadrement', () => {
    const conf = readFileSync(new URL('web/nginx/default.conf.template', RACINE), 'utf8')
    const attendus = ['Content-Security-Policy', 'X-Content-Type-Options', 'Referrer-Policy', 'Strict-Transport-Security',
      'Permissions-Policy', 'Cross-Origin-Opener-Policy', 'frame-ancestors', 'server_tokens off']
    const ecarts = attendus.filter((x) => !conf.includes(x))
    consigner('07-entetes-frontal', { ecarts })
    expect(ecarts).toEqual([])
  })
})
