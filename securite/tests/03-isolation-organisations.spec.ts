import { expect, test } from '@playwright/test'
import { appeler, cle, estPublique, inventaire, remplir } from '../outils/api'
import { application, canari } from '../outils/applications'
import { consigner } from '../outils/consigner'

/**
 * OWASP A01 (IDOR) — Étanchéité entre organisations. L'application A pose un canari ; l'application B, d'une autre
 * organisation, balaie toute l'inventaire avec les identifiants de A : rien ne doit être lu, modifié ni supprimé.
 */
test.describe('INT-03 Isolation entre organisations [A01]', () => {
  test('aucune opération de B n\'atteint ni ne révèle les objets de A', async () => {
    const a = await application()
    const b = await application()
    const c = await canari(a)
    const valeurs = { id: c.enveloppe, enveloppe: c.enveloppe, signataire: c.signataire, '*': c.enveloppe }
    const lignes: { operation: string; statut: number }[] = []
    const fuites: string[] = []
    for (const op of (await inventaire()).filter((o) => !estPublique(o) && !o.chemin.startsWith('/api/v1/signataire'))) {
      const r = await appeler(b.ctx, op.methode, remplir(op, valeurs), op.corps ? {} : undefined)
      lignes.push({ operation: cle(op), statut: r.statut })
      if (r.texte.includes(c.marque)) fuites.push(`${cle(op)} (${r.statut})`)
    }
    const acceptees = lignes.filter((l) => l.statut >= 200 && l.statut < 300 && /\{(id|enveloppe|signataire)\}/.test(l.operation))
    const intact = await (await a.ctx.get(`/api/v1/envelopes/${c.enveloppe}`)).json()
    consigner('03-isolation', { eprouvees: lignes.length, fuites, acceptees })
    expect(fuites, 'réponses de B contenant le canari de A').toEqual([])
    expect(acceptees, 'opérations de B acceptées sur les objets de A').toEqual([])
    expect(intact.statut, 'canari intact').toBe('brouillon')
  })
})
