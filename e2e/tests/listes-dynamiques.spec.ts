import { expect, test } from '@playwright/test'
import { base } from './base'

/** CLAUDE.md, règle 6 : une valeur ajoutée au référentiel apparaît dans l'interface sans redéploiement, puis disparaît. */
test('un référentiel ajouté en base apparaît dans le portail, puis disparaît une fois retiré', async ({ page }) => {
  const code = `e2e-${Date.now().toString(36)}`
  const db = await base()
  try {
    await db.query('INSERT INTO referentiel_type (code, libelles) VALUES ($1, $2)', [code, { fr: `Essai ${code}`, en: `Trial ${code}` }])
    await db.query(`INSERT INTO referentiel_valeur (type_id, code, libelles, ordre)
                    SELECT id, 'valeur-essai', $2, 1 FROM referentiel_type WHERE code = $1`, [code, { fr: 'Valeur d\'essai', en: 'Trial value' }])

    await page.goto('/referentiels')
    const section = page.getByRole('region', { name: `Essai ${code}` })
    await expect(section).toBeVisible()
    await expect(section.getByRole('cell', { name: 'Valeur d\'essai' })).toBeVisible()

    await db.query('DELETE FROM referentiel_type WHERE code = $1', [code])
    await page.reload()
    await expect(page.getByRole('heading', { level: 1 })).toBeVisible()
    await expect(page.getByRole('region', { name: `Essai ${code}` })).toHaveCount(0)
  } finally {
    await db.query('DELETE FROM referentiel_type WHERE code = $1', [code])
    await db.end()
  }
})
