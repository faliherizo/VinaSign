import { expect, test } from '@playwright/test'
import { request as fabrique } from '@playwright/test'
import { BASE } from '../outils/api'
import { application } from '../outils/applications'
import { consigner } from '../outils/consigner'

/** OWASP A04 · A08 — fichiers téléversés : type réel, structure, taille ; un refus dit pourquoi, sans trace interne. */
test.describe('INT-09 Fichiers téléversés [A04 · A08]', () => {
  test('faux PDF, PDF tronqué, contenu vide et fichier hors type sont refusés explicitement', async () => {
    const a = await application()
    const e = await (await a.ctx.post('/api/v1/envelopes', { headers: { 'Idempotency-Key': crypto.randomUUID() }, data: { externalReference: `FICH-${crypto.randomUUID()}`, mode: 'document',
      expiresAt: new Date(Date.now() + 86_400_000).toISOString() } })).json()
    const anonyme = await fabrique.newContext({ baseURL: BASE })
    const essais: [string, Buffer, string][] = [
      ['faux PDF (exécutable)', Buffer.from('MZ\x90\x00 pas un pdf'), 'document-type'],
      ['PDF tronqué', Buffer.from('%PDF-1.7\n1 0 obj'), 'document-structure'],
      ['contenu vide', Buffer.alloc(0), 'document-taille'],
      ['script', Buffer.from('<script>alert(1)</script>'), 'document-type'],
    ]
    const ecarts: string[] = []
    for (const [nom, contenu, attendu] of essais) {
      const depot = await (await a.ctx.post(`/api/v1/envelopes/${e.id}/documents`)).json()
      const recu = await anonyme.put(new URL(depot.url).pathname, { data: contenu, headers: { 'Content-Type': 'application/pdf' } })
      const r = recu.status() >= 400 ? recu : await a.ctx.post(`/api/v1/envelopes/${e.id}/documents/${depot.id}/confirmation`)
      const texte = await r.text()
      if (!texte.includes(attendu)) ecarts.push(`${nom} : ${r.status()} ${texte.slice(0, 120)}`)
      if (/Exception|at [a-z]+\.[a-z]+\./.test(texte)) ecarts.push(`${nom} : trace interne divulguée`)
    }
    consigner('09-fichiers', { essais: essais.length, ecarts })
    expect(ecarts).toEqual([])
  })
})
