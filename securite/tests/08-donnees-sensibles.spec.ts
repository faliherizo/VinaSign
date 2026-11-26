import { expect, test } from '@playwright/test'
import { application } from '../outils/applications'
import { consigner } from '../outils/consigner'

/** OWASP A04 · A08 — affectation de masse refusée ou ignorée ; aucun secret ni renseignement superflu dans les réponses. */
test.describe('INT-08 Données sensibles et affectation de masse [A04 · A08]', () => {
  test('les champs que l\'appelant ne doit pas poser sont ignorés', async () => {
    const a = await application()
    const b = await application()
    const r = await a.ctx.post('/api/v1/envelopes', { headers: { 'Idempotency-Key': crypto.randomUUID() }, data: { externalReference: `MASSE-${crypto.randomUUID()}`, mode: 'empreinte',
      expiresAt: new Date(Date.now() + 86_400_000).toISOString(), statut: 'terminee', id: crypto.randomUUID(), organisation: b.app.organisation,
      application: b.app.clientId } })
    const e = await r.json()
    consigner('08-affectation-masse', { statut: r.status(), corps: e })
    expect(r.status()).toBe(201)
    expect(e.statut).toBe('brouillon')
    expect(JSON.stringify(e)).not.toContain(b.app.organisation)
    expect((await b.ctx.get(`/api/v1/envelopes/${e.id}`)).status(), 'reste chez A').toBe(404)
  })

  test('le secret d\'un webhook n\'est rendu qu\'une fois, et jamais dans une liste', async () => {
    const a = await application()
    const creee = await (await a.ctx.post('/api/v1/webhooks', { data: { url: 'https://recepteur.exemple.ca/vinasign' } })).json()
    const liste = await (await a.ctx.get('/api/v1/webhooks')).text()
    expect(creee.secret).toBeTruthy()
    expect(liste).not.toContain(creee.secret)
  })
})
