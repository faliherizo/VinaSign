import { expect } from '@playwright/test'
import { When, Then } from './fixtures'
import { ORIGINE_HOTE } from '../outils/application'

/**
 * Intégration de la signature dans la page d'une application (VS-055). La page hôte est servie par Playwright sur
 * l'origine déclarée par l'application d'essai ; elle crée l'iframe comme @vinasign/embed (titre, referrerpolicy
 * « origin ») et consigne les avis reçus, en ne retenant que ceux de l'origine de VinaSign.
 */

function pageHote(url: string): string {
  const origine = new URL(url).origin
  return `<!doctype html><html lang="fr"><head><meta charset="utf-8"><title>Application hôte</title></head><body>
<main><h1>Dossier de l'employé</h1><ol id="avis" aria-label="Avis reçus"></ol><div id="conteneur"></div></main>
<script>
  const iframe = document.createElement('iframe');
  iframe.src = ${JSON.stringify(url)}; iframe.title = 'Signer le document'; iframe.referrerPolicy = 'origin';
  iframe.style.cssText = 'width:100%;height:1400px;border:0';
  addEventListener('message', (e) => {
    if (e.origin !== ${JSON.stringify(origine)} || e.source !== iframe.contentWindow || e.data?.source !== 'vinasign') return;
    const li = document.createElement('li'); li.textContent = e.data.avis; document.getElementById('avis').append(li);
  });
  document.getElementById('conteneur').append(iframe);
</script></body></html>`
}

When('l\'application affiche la session intégrée dans sa page', async ({ page, monde }) => {
  const url = String(monde.variables.integration)
  await page.route(`${ORIGINE_HOTE}/**`, (r) => r.fulfill({ contentType: 'text/html', body: pageHote(url) }))
  // Chrome (Local Network Access) refuse à une page publique d'intégrer une adresse locale sans permission. En campagne,
  // l'écran du signataire tourne sur localhost ; en exploitation, il a sa propre origine publique en HTTPS.
  await page.context().grantPermissions(['local-network-access'], { origin: ORIGINE_HOTE })
  await page.goto(`${ORIGINE_HOTE}/dossiers/42`)
})

When('le signataire consent et signe en tapant son nom dans la page de l\'application', async ({ page }) => {
  const cadre = page.frameLocator('iframe[title="Signer le document"]')
  await cadre.getByRole('checkbox', { name: /J'ai lu et j'accepte/i }).check()
  await cadre.getByRole('button', { name: /Continuer/i }).click()
  await cadre.getByLabel(/Titre du poste/i).fill('Directrice')
  await cadre.getByRole('radio', { name: /Taper mon nom/i }).click()
  await cadre.getByLabel(/Votre nom complet/i).fill('Camille Gagnon')
  await cadre.getByRole('button', { name: /Signer le document/i }).click()
  await expect(cadre.getByText(/Merci, votre signature est enregistrée/i)).toBeVisible()
})

Then('l\'application reçoit les avis {string}', async ({ page }, attendus: string) => {
  await expect(page.locator('#avis li')).toHaveText(attendus.split(/\s*,\s*/))
})
