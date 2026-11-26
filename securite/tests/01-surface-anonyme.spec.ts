import { expect, test } from '@playwright/test'
import { anonyme, appeler, cle, estPublique, inventaire, remplir } from '../outils/api'
import { consigner } from '../outils/consigner'
import charges from '../donnees/charges.json' with { type: 'json' }

/** Refus par défaut : toute opération non déclarée publique refuse une requête anonyme. */
test.describe('INT-01 Surface anonyme [A01 · A07]', () => {
  test('chaque opération non publique de l\'inventaire refuse l\'accès anonyme par un 401', async () => {
    const operations = await inventaire()
    const anon = await anonyme()
    const lignes: { operation: string; statut: number }[] = []
    for (const op of operations.filter((o) => !estPublique(o))) {
      const r = await appeler(anon, op.methode, remplir(op), op.corps ? {} : undefined)
      lignes.push({ operation: cle(op), statut: r.statut })
    }
    const ecarts = lignes.filter((l) => l.statut !== 401)
    consigner('01-surface-anonyme', { libelle: 'opérations non publiques éprouvées sans authentification', volume: lignes.length, total: operations.length, ecarts })
    expect(ecarts, 'opérations qui ne répondent pas 401 sans authentification').toEqual([])
  })

  test('les opérations publiques ne divulguent ni erreur interne, ni trace, ni secret', async () => {
    const operations = (await inventaire()).filter(estPublique)
    const anon = await anonyme()
    const ecarts: string[] = []
    for (const op of operations) {
      const r = await appeler(anon, op.methode, remplir(op, { '*': 'inexistant' }), op.corps ? {} : undefined)
      if (r.statut >= 500) ecarts.push(`${cle(op)} → ${r.statut}`)
      for (const s of charges.secretsInterdits) if (r.texte.includes(s)) ecarts.push(`${cle(op)} divulgue « ${s} »`)
      if (/at [a-z]+\.[a-z]+\.[A-Za-z]+\(|Exception:|SQLState/.test(r.texte)) ecarts.push(`${cle(op)} divulgue une trace d'exécution`)
    }
    consigner('01-surface-publique', { libelle: 'opérations publiques inspectées', volume: operations.length, publiques: operations.map(cle), ecarts })
    expect(operations.length, 'la surface publique déclarée existe dans l\'inventaire').toBeGreaterThan(0)
    expect(ecarts).toEqual([])
  })
})
