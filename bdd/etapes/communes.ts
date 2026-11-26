import { expect, type Page } from '@playwright/test'
import AxeBuilder from '@axe-core/playwright'
import { Given, When, Then, test } from './fixtures'
import { cas, ecrans } from '../outils/donnees'
import { executer, substituer } from '../outils/cas'

/** Étapes communes, en langage d'analyste : qui agit, ce qu'il fait, ce que la plateforme doit montrer ou rendre. */

const AXE_NORMES = ['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa', 'wcag22aa']

Given('j\'agis en tant que {string}', async ({ monde }, profil: string) => {
  monde.variables.profil = profil
})

Given('j\'utilise un téléphone', async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 })
})

When('j\'exécute le cas {string}', async ({ playwright, baseURL, monde }, nom: string) => {
  const c = cas[nom]
  if (!c) throw new Error(`Cas inconnu : « ${nom} » (bdd/donnees/*.json)`)
  // Le profil déclaré par le cas prime : il décrit qui agit ; à défaut, la personne connectée dans le scénario.
  const profil = c.profil ?? (monde.variables.profil as string | undefined)
  if (!profil && !c.profil) throw new Error(`Cas « ${nom} » : aucun profil (étape « je suis connecté en tant que … » ou champ profil)`)
  await executer(playwright, baseURL!, profil ?? c.profil!, nom, c, monde)
})

Then('la plateforme se comporte comme attendu', async ({ monde }) => {
  expect(monde.ecarts, 'écarts relevés par les vérifications des cas').toEqual([])
})

When('j\'ouvre l\'écran {string}', async ({ page, monde }, nom: string) => {
  const e = ecrans[nom]
  if (!e) throw new Error(`Écran inconnu : « ${nom} » (bdd/donnees/*.json)`)
  if (e.largeur) await page.setViewportSize({ width: e.largeur, height: 900 })
  await page.goto(substituer(e.chemin, monde.variables))
  await expect(page.locator('main')).toBeVisible()
  await expect(page.getByRole('heading', { name: new RegExp(substituer(e.titre, monde.variables), 'i') }).first()).toBeVisible()
})

When('je saisis {string} dans le champ {string}', async ({ page, monde }, valeur: string, champ: string) => {
  await page.getByRole('searchbox', { name: new RegExp(champ, 'i') }).or(page.getByLabel(new RegExp(champ, 'i'))).first()
    .fill(substituer(valeur, monde.variables))
})

When('je clique sur {string}', async ({ page, monde }, libelle: string) => {
  const nom = new RegExp(substituer(libelle, monde.variables), 'i')
  await page.getByRole('button', { name: nom }).or(page.getByRole('link', { name: nom })).or(page.getByRole('radio', { name: nom })).first().click()
})

/**
 * Signature manuscrite (VS-051) : un tracé continu au pointeur dans le cadre désigné par son nom accessible, comme le
 * ferait la souris, le doigt ou le stylet ; deux boucles et un trait, pour une image qui ne soit pas un simple point.
 */
When('je dessine ma signature dans le {string}', async ({ page, monde }, cadre: string) => {
  const toile = page.getByRole('img', { name: new RegExp(substituer(cadre, monde.variables), 'i') }).first()
  await toile.scrollIntoViewIfNeeded()
  const b = (await toile.boundingBox())!
  const point = (x: number, y: number) => [b.x + b.width * x, b.y + b.height * y] as const
  const trace: [number, number][] = [[0.1, 0.6], [0.18, 0.3], [0.24, 0.7], [0.3, 0.35], [0.38, 0.65], [0.46, 0.4], [0.55, 0.6],
    [0.62, 0.3], [0.7, 0.62], [0.8, 0.45], [0.9, 0.55]]
  await page.mouse.move(...point(...trace[0]))
  await page.mouse.down()
  for (const [x, y] of trace.slice(1)) await page.mouse.move(...point(x, y), { steps: 6 })
  await page.mouse.up()
})

When('je coche {string}', async ({ page, monde }, libelle: string) => {
  await page.getByRole('checkbox', { name: new RegExp(substituer(libelle, monde.variables), 'i') }).first().check()
})

When('je choisis {string} dans la liste {string}', async ({ page, monde }, choix: string, liste: string) => {
  const select = page.getByRole('combobox', { name: new RegExp(`^${liste}$`, 'i') }).first()
  const option = await select.locator('option').filter({ hasText: substituer(choix, monde.variables) }).first().getAttribute('value')
  await select.selectOption(option!)
})

Then('je vois {string}', async ({ page, monde }, texte: string) => {
  await expect(page.getByText(substituer(texte, monde.variables), { exact: false }).first()).toBeVisible()
})

Then('je vois le bouton {string}', async ({ page }, nom: string) => {
  await expect(page.getByRole('button', { name: new RegExp(nom, 'i') }).first()).toBeVisible()
})

Then('je vois le lien {string}', async ({ page }, nom: string) => {
  await expect(page.getByRole('link', { name: new RegExp(nom, 'i') }).first()).toBeVisible()
})

Then('je ne vois pas le lien {string}', async ({ page }, nom: string) => {
  await expect(page.getByRole('link', { name: new RegExp(`^${nom}$`, 'i') })).toHaveCount(0)
})

/** SGQRI 008 3.0 / WCAG 2.2 AA : axe-core sans violation, cibles ≥ 24 px, aucun débordement ni superposition. */
async function controlerAccessibilite(page: Page): Promise<void> {
  const bilan = await page.evaluate(() => {
    const visibles = (sel: string) => [...document.querySelectorAll<HTMLElement>(sel)].filter((e) => {
      const r = e.getBoundingClientRect()
      // Sous une modale ouverte, la page est inerte (ni focus ni clic) : seule la modale compte.
      return r.width > 0 && r.height > 0 && r.bottom > 0 && !e.closest('[inert]')
        && e.checkVisibility({ visibilityProperty: true, contentVisibilityAuto: true })
    })
    const interactifs = visibles('a[href], button, input:not([type=hidden]), select, textarea')
    const petits = interactifs.filter((e) => {
      const r = e.getBoundingClientRect()
      const enLigne = e.tagName === 'A' && getComputedStyle(e).display === 'inline'
      return !enLigne && (r.width < 24 || r.height < 24) && !(e as HTMLInputElement).type?.match(/checkbox|radio/)
    }).map((e) => e.outerHTML.slice(0, 80))
    const chevauchements: string[] = []
    for (let i = 0; i < interactifs.length; i++) {
      for (let j = i + 1; j < interactifs.length; j++) {
        const a = interactifs[i], b = interactifs[j]
        if (a.contains(b) || b.contains(a) || a.closest('label')?.contains(b) || b.closest('label')?.contains(a)) continue
        const ra = a.getBoundingClientRect(), rb = b.getBoundingClientRect()
        const l = Math.max(0, Math.min(ra.right, rb.right) - Math.max(ra.left, rb.left))
        const h = Math.max(0, Math.min(ra.bottom, rb.bottom) - Math.max(ra.top, rb.top))
        if (l > 2 && h > 2) chevauchements.push(`${a.textContent?.trim().slice(0, 30)} / ${b.textContent?.trim().slice(0, 30)}`)
      }
    }
    const deborde = document.documentElement.scrollWidth > window.innerWidth + 1
    // Éléments qui dépassent le bord droit sans qu'aucun de leurs descendants ne dépasse : les vrais responsables.
    const depassent = (e: Element) => e.getBoundingClientRect().right > window.innerWidth + 1
    const debordants = deborde ? [...document.body.querySelectorAll('*')].filter((e) => depassent(e) && ![...e.children].some(depassent)
      && !e.closest('.defilement')).map((e) => e.outerHTML.slice(0, 80)) : []
    return { deborde, debordants, petits, chevauchements }
  })
  expect(bilan.debordants, 'débordement horizontal (éléments en cause)').toEqual([])
  expect(bilan.deborde, 'débordement horizontal').toBe(false)
  expect(bilan.petits, 'cibles < 24 px').toEqual([])
  expect(bilan.chevauchements, 'éléments superposés').toEqual([])
  const { violations } = await new AxeBuilder({ page }).withTags(AXE_NORMES).analyze()
  expect(violations.map((v) => `${v.id} (${v.nodes.length}) : ${v.nodes[0]?.target.join(' ')}`), 'violations axe-core').toEqual([])
}

Then('l\'écran est accessible, sans débordement ni superposition', async ({ page }) => {
  await controlerAccessibilite(page)
})

/** Même contrôle, à chaque largeur de référence (téléphone, tablette, portable, grand écran), dans l'état courant. */
Then('l\'écran est accessible à {int}, {int}, {int} et {int} px de large', async ({ page }, a: number, b: number, c: number, d: number) => {
  for (const largeur of [a, b, c, d]) {
    await page.setViewportSize({ width: largeur, height: 900 })
    await test.step(`largeur ${largeur} px`, () => controlerAccessibilite(page))
  }
})
