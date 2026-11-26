import { APIRequestContext, request as fabrique } from '@playwright/test'
import { enregistrer, jeton, type ApplicationEssai } from '../../bdd/outils/application.ts'
import { BASE } from './api'

/**
 * Applications d'essai de la campagne (outillage partagé avec bdd/ : enregistrement en base, jeton private_key_jwt).
 * Chaque application vit dans sa propre organisation.
 */
export type Client = { app: ApplicationEssai; ctx: APIRequestContext }

export const TOUTES_PORTEES = ['enveloppes-creer', 'enveloppes-lire', 'enveloppes-envoyer', 'signataires-gerer', 'documents-deposer',
  'sessions-creer', 'webhooks-gerer', 'empreintes-signer', 'preuves-lire', 'documents-telecharger']

export async function application(portees: string[] = TOUTES_PORTEES): Promise<Client> {
  const app = await enregistrer(portees)
  const acces = await jeton(BASE, app, portees)
  return { app, ctx: await fabrique.newContext({ baseURL: BASE, extraHTTPHeaders: { Authorization: `Bearer ${acces}` } }) }
}

/** Enveloppe « canari » de A, marquée d'une référence unique, avec un signataire ; rend ses identifiants. */
export async function canari(a: Client): Promise<{ marque: string; enveloppe: string; signataire: string }> {
  const marque = `CANARI-${crypto.randomUUID()}`
  const e = await (await a.ctx.post('/api/v1/envelopes', { headers: { 'Idempotency-Key': crypto.randomUUID() }, data: { externalReference: marque, mode: 'empreinte',
    expiresAt: new Date(Date.now() + 86_400_000).toISOString() } })).json()
  const s = await (await a.ctx.put(`/api/v1/envelopes/${e.id}/signers`, { data: [{ nom: marque, courriel: 'canari@exemple.ca', langue: 'fr',
    type: 'invite', ordre: 1 }] })).json()
  return { marque, enveloppe: e.id, signataire: s.signataires[0].id }
}
