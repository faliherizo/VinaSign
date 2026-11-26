import { expect } from '@playwright/test'
import { When } from './fixtures'
import { readFileSync } from 'fs'
import { join } from 'path'
import { PDF_MINIMAL, substituer } from '../outils/cas'

/**
 * Connexion au portail par son fournisseur d'identité (VS-ADR-009) : code d'autorisation avec PKCE, chez le fournisseur
 * OIDC du bac à sable en connexion interactive. La personne saisit son identité et ses revendications (courriel
 * attesté, méthode d'authentification, audience de l'API), comme le ferait l'annuaire de son organisation.
 */
When('je me connecte au portail en tant que {string}', async ({ page, monde }, personne: string) => {
  const courriel = substituer(personne, monde.variables)
  await page.goto('/')
  await page.getByRole('button', { name: /^Se connecter$/i }).first().click()
  await page.locator('input[name="username"]').fill(courriel)
  await page.locator('textarea[name="claims"]').fill(JSON.stringify({
    email: courriel, email_verified: true, amr: ['pwd'], aud: [process.env.VINASIGN_PORTAIL_AUDIENCE ?? 'api://vinasign-bdd'],
  }))
  await page.getByRole('button', { name: /Sign-in/i }).or(page.locator('input[type=submit]')).first().click()
  await expect(page.getByRole('button', { name: /Se déconnecter/i })).toBeVisible()
})

/** Document joint au formulaire : un PDF valide d'une page, produit par le harnais (aucun fichier figé). */
When('je joins un document PDF dans le champ {string}', async ({ page }, champ: string) => {
  await page.getByLabel(new RegExp(champ, 'i')).setInputFiles({ name: 'contrat.pdf', mimeType: 'application/pdf', buffer: PDF_MINIMAL })
})

/** Document Word d'essai (bdd/donnees/fichiers/contrat.docx) : la plateforme le convertit en PDF/A au dépôt (VS-035). */
When('je joins le document Word {string} dans le champ {string}', async ({ page }, fichier: string, champ: string) => {
  await page.getByLabel(new RegExp(champ, 'i')).setInputFiles({ name: fichier, mimeType: 'application/vnd.openxmlformats-officedocument.wordprocessingml.document',
    buffer: readFileSync(join(process.cwd(), 'donnees/fichiers', fichier)) })
})
