import { expect, test, type Page } from '@playwright/test'
import { mkdirSync, readFileSync, writeFileSync } from 'fs'
import { dirname, join } from 'path'
import { fileURLToPath } from 'url'
import { cas } from '../outils/donnees'
import { executer, substituer, type Contexte } from '../outils/cas'

/**
 * Démonstration filmée de VinaSign : un vrai contrat PDF préparé au portail, signé à la main puis au clavier, scellé et
 * horodaté. C'est un test avant d'être un film : chaque geste est suivi d'une vérification, et la vidéo
 * (outils/video.mjs) ne montre que ce qui a été constaté. Données : donnees/demonstration.json.
 *
 *   node outils/video.mjs voix && node outils/video.mjs tourner && node outils/video.mjs monter
 */
const ICI = dirname(fileURLToPath(import.meta.url))
const DONNEES = JSON.parse(readFileSync(join(ICI, '../donnees/demonstration.json'), 'utf8'))
const DUREES: Record<string, number> = process.env.DEMO_DUREES ? JSON.parse(readFileSync(process.env.DEMO_DUREES, 'utf8')) : {}
const SORTIE = join(ICI, '../test-results-demo')

type Moment = { t: number; voix: string; texte: string }

test('Démonstration — signer un contrat électroniquement, de bout en bout', async ({ page, playwright, baseURL }) => {
  const unique = Date.now().toString(36)
  const jour = (n: number) => new Date(Date.now() + n * 86_400_000).toISOString().slice(0, 10)
  const signataireUrl = process.env.BDD_SIGNATAIRE_URL ?? 'http://localhost:4301'
  const ctx: Contexte = { variables: { unique, UNIQUE: unique.toUpperCase(), dansUnMois: jour(30), signataire: signataireUrl }, ecarts: [] }
  const film = substituer(DONNEES.film, ctx.variables) as typeof DONNEES.film
  const debut = Date.now()
  const chronologie: Moment[] = []
  /** Narration : posée sur la chronologie, puis le temps de la dire avant le geste suivant. */
  const dire = async (cle: string) => {
    const texte = DONNEES.narration[cle]
    chronologie.push({ t: Date.now() - debut, voix: texte, texte })
    await page.waitForTimeout(Math.round(((DUREES[texte] ?? 3.5) + 0.5) * 1000))
  }
  /** Connexion de la préparatrice par le fournisseur d'identité (bac à sable OIDC en connexion interactive). */
  const connecter = async () => {
    await page.getByRole('button', { name: /^Se connecter$/i }).first().click()
    await page.locator('input[name="username"]').fill(film.preparatrice.courriel)
    await page.locator('textarea[name="claims"]').fill(JSON.stringify({ email: film.preparatrice.courriel, email_verified: true, amr: ['pwd'],
      aud: [process.env.VINASIGN_PORTAIL_AUDIENCE ?? 'api://vinasign-bdd'] }))
    await page.locator('input[type=submit]').click()
    await expect(page.getByRole('button', { name: /Se déconnecter/i })).toBeVisible()
  }
  const jouer = async (nom: string) => {
    await executer(playwright, baseURL!, cas[nom].profil!, nom, cas[nom], ctx)
    expect(ctx.ecarts, `écarts du cas ${nom}`).toEqual([])
  }

  await jouer('demo.preparatrice')

  await page.goto('/')
  await expect(page.getByRole('heading', { level: 1 })).toBeVisible()
  await dire('accueil')

  // Connexion par le fournisseur d'identité de l'organisation (bac à sable OIDC, connexion interactive).
  await dire('connexion')
  await connecter()

  await page.getByRole('link', { name: /^Enveloppes$/i }).click()
  await page.getByRole('link', { name: /Nouvelle enveloppe/i }).click()
  await dire('nouvelle')
  await page.getByLabel(/Référence/i).pressSequentially(film.reference, { delay: 40 })
  await page.getByLabel(/Date limite/i).fill(jour(30))
  await page.getByLabel(/Document à signer/i).setInputFiles(join(ICI, '../donnees', film.document))
  await dire('signataires')
  const rangees = page.locator('.signataire')
  for (const [i, s] of film.signataires.entries()) {
    if (i > 0) await page.getByRole('button', { name: /Ajouter un signataire/i }).click()
    await rangees.nth(i).getByLabel(/Nom complet/i).pressSequentially(s.nom, { delay: 40 })
    await rangees.nth(i).getByLabel(/Courriel/i).pressSequentially(s.courriel, { delay: 20 })
  }
  await page.getByRole('button', { name: /Placer les signatures sur le document/i }).click()

  await expect(page.getByText('Page 2 / 2')).toBeVisible({ timeout: 60_000 })
  await dire('visualisation')
  await dire('zones')
  const surfaces = page.locator('.surface')
  for (const z of film.zones) {
    await page.getByLabel(/Signataire de la zone/i).selectOption({ label: z.signataire })
    await page.getByLabel(/Type de zone/i).selectOption({ label: z.type })
    const surface = surfaces.nth(z.page - 1)
    await surface.scrollIntoViewIfNeeded()
    // La page est dessinée de façon asynchrone (PDF.js) : on attend sa vraie hauteur (format Lettre) avant de viser.
    await expect.poll(async () => (await surface.boundingBox())?.height ?? 0, { timeout: 30_000 }).toBeGreaterThan(500)
    // Le point visé au milieu de l'écran : un clic hors de la fenêtre ne touche rien.
    const avant = (await surface.boundingBox())!
    await page.evaluate((dy) => window.scrollBy({ top: dy, behavior: 'smooth' }), avant.y + avant.height * z.y - 360)
    await page.waitForTimeout(900)
    const b = (await surface.boundingBox())!
    await page.mouse.click(b.x + b.width * z.x, b.y + b.height * z.y)
    await expect(page.getByRole('button', { name: new RegExp(`${z.type} — ${z.signataire}`) }).first()).toBeVisible()
    await page.waitForTimeout(700)
  }
  await expect(page.getByText(`${film.zones.length} zone(s) placée(s)`)).toBeVisible()
  await dire('envoi')
  await page.getByRole('button', { name: /Envoyer pour signature/i }).click()
  await expect(page.getByText(film.reference)).toBeVisible()

  // Première signataire : lien reçu par courriel, consentement, lecture, signature dessinée.
  await jouer('demo.lien-lea')
  await dire('courriel')
  await page.goto(`${signataireUrl}/s/${ctx.variables.lien}`)
  await page.getByRole('checkbox', { name: /J'ai lu et j'accepte/i }).check()
  await dire('consentement')
  await page.getByRole('button', { name: /^Continuer$/i }).click()
  await expect(page.getByText(/Page 2 \/ 2/)).toBeVisible({ timeout: 60_000 })
  await page.mouse.wheel(0, 900)
  await page.waitForTimeout(1500)
  await page.mouse.wheel(0, 900)
  await dire('dessin')
  await page.getByRole('radio', { name: /Dessiner/i }).click()
  const cadre = page.getByRole('img', { name: /Cadre de signature/i })
  await cadre.scrollIntoViewIfNeeded()
  const c = (await cadre.boundingBox())!
  // Deux traits, comme une main : une écriture liée à boucles, puis un paraphe souligné.
  const ecriture = Array.from({ length: 140 }, (_, k) => {
    const t = k / 139
    return [0.07 + 0.7 * t + 0.035 * Math.sin(2 * Math.PI * 6 * t), 0.5 - 0.22 * Math.cos(2 * Math.PI * 6 * t) * (0.6 + 0.4 * Math.sin(Math.PI * t))] as const
  })
  const paraphe = Array.from({ length: 40 }, (_, k) => {
    const t = k / 39
    return [0.12 + 0.8 * t, 0.8 - 0.08 * Math.sin(Math.PI * t) - 0.04 * t] as const
  })
  for (const trait of [ecriture, paraphe]) {
    await page.mouse.move(c.x + c.width * trait[0][0], c.y + c.height * trait[0][1])
    await page.mouse.down()
    for (const [x, y] of trait.slice(1)) await page.mouse.move(c.x + c.width * x, c.y + c.height * y, { steps: 2 })
    await page.mouse.up()
  }
  await page.getByRole('button', { name: /Signer le document/i }).click()
  await expect(page.getByText(/Merci, votre signature est enregistrée/i)).toBeVisible()

  // Second signataire, invité seulement maintenant : nom tapé au clavier.
  await jouer('demo.lien-jean')
  await dire('second')
  await page.goto(`${signataireUrl}/s/${ctx.variables.lien}`)
  await page.getByRole('checkbox', { name: /J'ai lu et j'accepte/i }).check()
  await page.getByRole('button', { name: /^Continuer$/i }).click()
  await expect(page.getByText(/Page 2 \/ 2/)).toBeVisible({ timeout: 60_000 })
  await page.getByRole('radio', { name: /Taper mon nom/i }).click()
  await page.getByLabel(/Votre nom complet/i).pressSequentially(film.signataires[1].nom, { delay: 60 })
  await page.getByRole('button', { name: /Signer le document/i }).click()
  await expect(page.getByText(/Merci, votre signature est enregistrée/i)).toBeVisible()

  // Scellement et horodatage, vérifiés ; puis le document final à l'écran.
  await jouer('demo.scelle')
  await dire('final')
  // Retour au portail : les jetons ne vivent qu'en mémoire (VS-ADR-009), la préparatrice se reconnecte.
  await page.goto('/')
  await connecter()
  await page.getByRole('link', { name: /^Enveloppes$/i }).click()
  await page.getByRole('link', { name: film.reference }).click()
  await expect(page.getByText('Document final signé et scellé')).toBeVisible({ timeout: 60_000 })
  await expect(page.getByText('Page 2 / 2')).toBeVisible({ timeout: 60_000 })
  await dire('resultat')
  await page.locator('.surface').nth(1).scrollIntoViewIfNeeded()
  await page.waitForTimeout(3000)
  await dire('fin')

  mkdirSync(SORTIE, { recursive: true })
  writeFileSync(join(SORTIE, 'demonstration.json'), JSON.stringify({ titre: DONNEES.film.titre, chronologie,
    video: join(test.info().outputDir, 'video.webm') }, null, 1))
})
