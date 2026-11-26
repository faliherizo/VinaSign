import { expect, test } from '@playwright/test'
import { request as fabrique } from '@playwright/test'
import { BASE, appeler, cle, estPublique, inventaire, remplir } from '../outils/api'
import { application } from '../outils/applications'
import { consigner } from '../outils/consigner'

const b64 = (o: unknown) => Buffer.from(JSON.stringify(o)).toString('base64url')

/** OWASP A07 — jetons forgés, expirés ou rejoués refusés sur tout l'inventaire ; un lien de signataire inconnu ne renseigne pas. */
test.describe('INT-05 Authentification et jetons [A07]', () => {
  test('un jeton forgé (alg none, signature fausse, expiré) est refusé partout', async () => {
    const t = Math.floor(Date.now() / 1000)
    const corps = { iss: process.env.VINASIGN_JETONS_EMETTEUR, sub: 'intrus', org: crypto.randomUUID(), client_id: 'intrus', scope: 'enveloppes-lire',
      iat: t, exp: t + 300 }
    const forges = [`${b64({ alg: 'none', typ: 'JWT' })}.${b64(corps)}.`, `${b64({ alg: 'RS256', typ: 'JWT' })}.${b64(corps)}.${'A'.repeat(342)}`,
      `${b64({ alg: 'HS256', typ: 'JWT' })}.${b64({ ...corps, exp: t - 60 })}.${'A'.repeat(43)}`]
    const ecarts: string[] = []
    let eprouvees = 0
    for (const jeton of forges) {
      const ctx = await fabrique.newContext({ baseURL: BASE, extraHTTPHeaders: { Authorization: `Bearer ${jeton}` } })
      for (const op of (await inventaire()).filter((o) => !estPublique(o))) {
        const r = await appeler(ctx, op.methode, remplir(op), op.corps ? {} : undefined)
        eprouvees++
        if (r.statut !== 401) ecarts.push(`${cle(op)} → ${r.statut}`)
      }
    }
    consigner('05-jetons-forges', { eprouvees, ecarts })
    expect(ecarts).toEqual([])
  })

  test('un jeton valide est accepté ; un lien de signataire inconnu répond toujours de la même façon', async () => {
    const a = await application()
    expect((await a.ctx.get('/api/v1/webhooks')).status(), 'jeton valide accepté').toBe(200)
    const anonyme = await fabrique.newContext({ baseURL: BASE })
    const statuts = new Set<number>()
    for (const jeton of ['inconnu', 'x'.repeat(43), crypto.randomUUID()]) {
      statuts.add((await anonyme.post('/api/v1/signataire/ouverture', { data: { jeton } })).status())
    }
    expect([...statuts], 'même réponse pour tout lien inconnu : aucune énumération').toEqual([404])
  })
})
