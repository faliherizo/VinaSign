import { expect, test } from '@playwright/test'
import { application } from '../outils/applications'
import { consigner } from '../outils/consigner'
import charges from '../donnees/charges.json' with { type: 'json' }

/** OWASP A10 — une destination de webhook ne désigne jamais un hôte interne, ni par adresse, ni par schéma, ni par identifiants. */
test.describe('INT-10 SSRF par les webhooks [A10]', () => {
  test('chaque adresse interne ou hors HTTPS est refusée', async () => {
    const a = await application()
    const internes = [...charges.ssrf, 'https://169.254.169.254/latest/meta-data/', 'https://127.0.0.1/', 'https://10.0.0.1/', 'https://[::1]/',
      'https://192.168.1.1/', 'https://100.64.0.1/', 'http://exemple.ca/', 'file:///etc/passwd', 'https://u:p@exemple.ca/']
    const acceptees: string[] = []
    for (const url of internes) {
      const r = await a.ctx.post('/api/v1/webhooks', { data: { url } })
      if (r.status() < 400) acceptees.push(`${url} → ${r.status()}`)
    }
    consigner('10-ssrf', { essais: internes.length, acceptees })
    expect(acceptees).toEqual([])
  })
})
