import { expect, test } from '@playwright/test'
import { anonyme, appeler, cle, estPublique, inventaire, remplir } from '../outils/api'
import { consigner } from '../outils/consigner'
import charges from '../donnees/charges.json' with { type: 'json' }

/** Injection : chaque paramètre de chemin et de requête des opérations atteignables reçoit chaque charge. */
test.describe('INT-06 Injection [A03]', () => {
  test('aucune charge d\'injection ne provoque d\'erreur serveur ni ne divulgue la base', async () => {
    const anon = await anonyme()
    const operations = (await inventaire()).filter(estPublique)
    const toutes = [...charges.injectionSql, ...charges.xss, ...charges.traversee]
    const ecarts: string[] = []
    let essais = 0
    for (const op of operations) {
      for (const charge of toutes) {
        const chemins = op.parametres.length ? [remplir(op, { '*': charge })] : [remplir(op)]
        for (const chemin of chemins) {
          const requete = op.requete.map((q) => `${q}=${encodeURIComponent(charge)}`).join('&')
          const r = await appeler(anon, op.methode, requete ? `${chemin}?${requete}` : chemin)
          essais++
          if (r.statut >= 500) ecarts.push(`${cle(op)} ← ${charge} → ${r.statut}`)
          if (/SQLState|PSQLException|syntax error at|org\.postgresql/i.test(r.texte)) ecarts.push(`${cle(op)} divulgue la base`)
          if (r.texte.includes('<script') && !(r.entetes['content-type'] ?? '').includes('json')) ecarts.push(`${cle(op)} renvoie du HTML non échappé`)
        }
      }
    }
    consigner('06-injection', { libelle: 'charges d\'injection envoyées', volume: essais, ecarts })
    expect(ecarts).toEqual([])
  })
})
