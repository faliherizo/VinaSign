/**
 * @vinasign/client (VS-093) : client Node de VinaSign. Les appels sont typés depuis le contrat OpenAPI v1
 * (serveur/contrat/openapi-v1.json) ; le jeton d'accès est obtenu par assertion signée (private_key_jwt) et mis en cache.
 * Aucune règle de sécurité ni liste métier n'y est codée : les listes viennent des référentiels de l'API.
 */
import { createSign, randomUUID, timingSafeEqual, createHmac, type KeyObject } from 'node:crypto'
import createClient, { type Client, type Middleware } from 'openapi-fetch'
import type { paths } from './genere/api.ts'

export type { paths }

export interface Identite {
  /** Adresse de l'API. */
  url: string
  /** Émetteur des jetons (audience des assertions) ; l'adresse de l'API par défaut. */
  emetteur?: string
  clientId: string
  /** Clé privée de l'application (RSA → RS256, EC P-256 → ES256). */
  cle: KeyObject
  kid?: string
  portees: string[]
}

const b64 = (x: Buffer | string) => Buffer.from(x).toString('base64url')

/** JWT compact signé par la clé de l'application ; ES256 au format JOSE (R‖S, IEEE P1363). */
export function signerJwt(cle: KeyObject, revendications: Record<string, unknown>, kid?: string): string {
  const alg = algorithme(cle)
  const entete = b64(JSON.stringify(kid ? { alg, typ: 'JWT', kid } : { alg, typ: 'JWT' }))
  const corps = b64(JSON.stringify(revendications))
  const signature = createSign('SHA256').update(`${entete}.${corps}`).sign(alg === 'ES256' ? { key: cle, dsaEncoding: 'ieee-p1363' } : cle)
  return `${entete}.${corps}.${b64(signature)}`
}

export function algorithme(cle: KeyObject): 'RS256' | 'ES256' {
  if (cle.asymmetricKeyType === 'rsa') return 'RS256'
  if (cle.asymmetricKeyType === 'ec' && cle.asymmetricKeyDetails?.namedCurve === 'prime256v1') return 'ES256'
  throw new Error('Clé refusée : RSA (RS256) ou EC P-256 (ES256).')
}

/** Jeton d'accès de l'application, mis en cache jusqu'à 30 secondes de son échéance. */
export class Jetons {
  private jeton?: string
  private expire = 0
  private enCours?: Promise<string>
  private readonly identite: Identite
  private readonly maintenant: () => number
  private readonly appeler: typeof fetch

  constructor(identite: Identite, maintenant: () => number = Date.now, appeler: typeof fetch = fetch) {
    this.identite = identite
    this.maintenant = maintenant
    this.appeler = appeler
  }

  async obtenir(): Promise<string> {
    if (this.jeton && this.maintenant() < this.expire - 30_000) return this.jeton
    this.enCours ??= this.demander().finally(() => { this.enCours = undefined })
    return this.enCours
  }

  private async demander(): Promise<string> {
    const t = Math.floor(this.maintenant() / 1000)
    const { url, emetteur, clientId, cle, kid, portees } = this.identite
    const assertion = signerJwt(cle, { iss: clientId, sub: clientId, aud: emetteur ?? url, jti: randomUUID(), iat: t, exp: t + 60 }, kid)
    const r = await this.appeler(new URL('/oauth2/token', url), {
      method: 'POST',
      headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
      body: new URLSearchParams({ grant_type: 'client_credentials', client_id: clientId, scope: portees.join(' '),
        client_assertion_type: 'urn:ietf:params:oauth:client-assertion-type:jwt-bearer', client_assertion: assertion }),
    })
    if (r.status !== 200) throw new Error(`Jeton refusé par VinaSign (HTTP ${r.status}) : ${await r.text()}`)
    const corps = (await r.json()) as { access_token: string; expires_in?: number }
    this.jeton = corps.access_token
    this.expire = this.maintenant() + (corps.expires_in ?? 60) * 1000
    return this.jeton
  }
}

/** Client typé : chaque requête porte le jeton de l'application. Ex. `client.GET('/api/v1/referentiels')`. */
export function creerClient(identite: Identite, jetons = new Jetons(identite)): Client<paths> {
  const client = createClient<paths>({ baseUrl: identite.url.replace(/\/+$/, '') })
  const autorisation: Middleware = {
    async onRequest({ request }) {
      request.headers.set('Authorization', `Bearer ${await jetons.obtenir()}`)
      return request
    },
  }
  client.use(autorisation)
  return client
}

/** Attestation d'un utilisateur de l'application (VS-044), à joindre à la demande de session intégrée. */
export function attester(identite: Identite, sujet: string, methodes: string[], authentifieLe: Date, maintenant = Date.now()): string {
  const t = Math.floor(maintenant / 1000)
  return signerJwt(identite.cle, { iss: identite.clientId, sub: sujet, aud: `${identite.emetteur ?? identite.url}/attestations`, jti: randomUUID(),
    iat: t, exp: t + 60, amr: methodes, auth_time: Math.floor(authentifieLe.getTime() / 1000) })
}

/**
 * Vérifie un webhook (VS-091) : en-tête VinaSign-Signature « t=<s>,v1=<HMAC-SHA256 de "t.corps"> », en temps constant,
 * refusé au-delà de la tolérance. Ensuite : dédupliquer par VinaSign-Event-Id et relire l'enveloppe par l'API.
 */
export function verifierWebhook(secret: string, entete: string | null | undefined, corps: Buffer, maintenant = Date.now(), toleranceSecondes = 300): boolean {
  const m = /^t=(\d{1,12}),v1=([0-9a-f]{64})$/.exec(entete ?? '')
  if (!m) return false
  const t = Number(m[1])
  const attendu = createHmac('sha256', secret).update(`${t}.`).update(corps).digest()
  return Math.abs(Math.floor(maintenant / 1000) - t) <= toleranceSecondes && timingSafeEqual(attendu, Buffer.from(m[2], 'hex'))
}
