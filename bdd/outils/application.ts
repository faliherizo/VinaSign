import { createSign, generateKeyPairSync, randomUUID, KeyObject } from 'crypto'
import pg from 'pg'

/**
 * Application cliente d'essai, telle que l'exploitation l'enregistrerait (VS-011, VS-012) : organisation active au plan
 * par défaut du référentiel, application de bac à sable avec sa clé publique et ses portées. Le harnais détient la clé
 * privée et obtient ses jetons comme le ferait le système appelant (client credentials, private_key_jwt).
 * Identifiants de la base tirés de l'environnement (.env), jamais écrits ici.
 */
export type ApplicationEssai = { clientId: string; organisation: string; cle: KeyObject }

function requis(nom: string): string {
  const v = process.env[nom]
  if (!v) throw new Error(`Variable ${nom} absente : charger .env (set -a; . ../.env; set +a).`)
  return v
}

export async function base(): Promise<pg.Client> {
  const c = new pg.Client({
    host: process.env.VINASIGN_DB_HOTE ?? 'localhost',
    port: Number(process.env.VINASIGN_DB_PORT ?? 55432),
    database: requis('VINASIGN_DB_NOM'),
    user: requis('VINASIGN_DB_UTILISATEUR'),
    password: requis('VINASIGN_DB_MDP'),
  })
  await c.connect()
  return c
}

/** Origine de la page d'essai qui intègre l'interface du signataire (VS-055) ; servie par Playwright, jamais résolue. */
export const ORIGINE_HOTE = 'https://hote-integration.vinasign.test'

export async function enregistrer(portees: string[]): Promise<ApplicationEssai> {
  const { publicKey, privateKey } = generateKeyPairSync('rsa', { modulusLength: 2048 })
  const jwk = { ...publicKey.export({ format: 'jwk' }), kid: randomUUID(), use: 'sig', alg: 'RS256' }
  const organisation = randomUUID()
  const clientId = `vs-bdd-${randomUUID()}`
  const db = await base()
  try {
    await db.query(`INSERT INTO organisation (id, nom, statut, plan, cree_le, modifie_le)
                    SELECT $1, $2, 'active', v.code, now(), now() FROM referentiel_valeur v JOIN referentiel_type t ON t.id = v.type_id
                    WHERE t.code = 'plan' AND v.attributs ->> 'parDefaut' = 'true' AND v.tenant_id IS NULL`, [organisation, `BDD ${organisation}`])
    const environnement = (await db.query(`SELECT v.code FROM referentiel_valeur v JOIN referentiel_type t ON t.id = v.type_id
                    WHERE t.code = 'environnement-application' AND v.tenant_id IS NULL AND v.actif ORDER BY v.ordre LIMIT 1`)).rows[0].code
    await db.query(`INSERT INTO application_cliente (tenant_id, client_id, nom, environnement, jwks, portees, origines_integration)
                    VALUES ($1, $2, $3, $4, $5, $6, $7)`, [organisation, clientId, `Application BDD`, environnement, { keys: [jwk] }, portees, [ORIGINE_HOTE]])
  } finally {
    await db.end()
  }
  return { clientId, organisation, cle: privateKey }
}

function base64url(x: Buffer | string): string {
  return Buffer.from(x).toString('base64url')
}

/** Assertion client RFC 7523 signée RS256 par la clé de l'application. */
export function assertion(a: ApplicationEssai, audience: string): string {
  const t = Math.floor(Date.now() / 1000)
  const entete = base64url(JSON.stringify({ alg: 'RS256', typ: 'JWT' }))
  const corps = base64url(JSON.stringify({ iss: a.clientId, sub: a.clientId, aud: audience, jti: randomUUID(), iat: t, exp: t + 60 }))
  const signature = createSign('RSA-SHA256').update(`${entete}.${corps}`).sign(a.cle)
  return `${entete}.${corps}.${base64url(signature)}`
}

/**
 * Attestation d'un utilisateur de l'application (VS-044) : JWT signé RS256 par la clé de l'application, destiné aux
 * attestations de VinaSign, avec ses méthodes d'authentification (amr) et leur heure (auth_time, il y a n secondes).
 */
export function attestation(a: ApplicationEssai, sujet: string, amr: string[], authIlYa: number): string {
  const t = Math.floor(Date.now() / 1000)
  const entete = base64url(JSON.stringify({ alg: 'RS256', typ: 'JWT' }))
  const corps = base64url(JSON.stringify({ iss: a.clientId, sub: sujet, aud: `${requis('VINASIGN_JETONS_EMETTEUR')}/attestations`,
    jti: randomUUID(), iat: t, exp: t + 60, amr, auth_time: t - authIlYa }))
  const signature = createSign('RSA-SHA256').update(`${entete}.${corps}`).sign(a.cle)
  return `${entete}.${corps}.${base64url(signature)}`
}

/** Jeton d'accès obtenu au point de jeton de VinaSign. */
export async function jeton(serveur: string, a: ApplicationEssai, portees: string[]): Promise<string> {
  const emetteur = requis('VINASIGN_JETONS_EMETTEUR')
  const r = await fetch(`${serveur}/oauth2/token`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
    body: new URLSearchParams({ grant_type: 'client_credentials', client_id: a.clientId, scope: portees.join(' '),
      client_assertion_type: 'urn:ietf:params:oauth:client-assertion-type:jwt-bearer', client_assertion: assertion(a, emetteur) }),
  })
  if (r.status !== 200) throw new Error(`Jeton refusé (${r.status}) : ${await r.text()}`)
  return (await r.json()).access_token
}

/** Fournisseur OIDC du portail (bac à sable oidc) : jeton d'une personne désignée par son courriel, avec ou sans MFA. */
export async function jetonPortail(courriel: string, mfa: boolean): Promise<string> {
  const fournisseur = process.env.BDD_OIDC_URL ?? `http://localhost:${process.env.VS_OIDC_PORT ?? 28094}/portail`
  const r = await fetch(`${fournisseur}/token`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
    body: new URLSearchParams({ grant_type: 'client_credentials', client_id: courriel, client_secret: 'bac-a-sable', scope: mfa ? 'mfa' : 'sans-mfa' }),
  })
  if (r.status !== 200) throw new Error(`Fournisseur OIDC du bac à sable injoignable (${r.status}) : docker compose -f infra/bacs-a-sable/docker-compose.yml up -d oidc`)
  return (await r.json()).access_token
}

/** Administrateur de la plateforme, créé par l'exploitation (aucune API ne le crée) ; rend son courriel. */
export async function administrateurPlateforme(): Promise<string> {
  const courriel = `plateforme-${randomUUID()}@exemple.ca`
  const db = await base()
  try {
    await db.query(`INSERT INTO utilisateur_portail (id, courriel, roles) VALUES ($1, $2, ARRAY['administrateur-plateforme'])`, [randomUUID(), courriel])
  } finally {
    await db.end()
  }
  return courriel
}

/** Trousseau de l'exécution : clés générées à la demande, désignées par un nom dans les cas (jamais de clé figée). */
const trousseau = new Map<string, { publique: Record<string, unknown>; privee: KeyObject }>()

function cleNommee(nom: string) {
  let c = trousseau.get(nom)
  if (!c) {
    const { publicKey, privateKey } = generateKeyPairSync('rsa', { modulusLength: 2048 })
    c = { publique: { ...publicKey.export({ format: 'jwk' }), kid: `${nom}-${randomUUID()}`, use: 'sig', alg: 'RS256' }, privee: privateKey }
    trousseau.set(nom, c)
  }
  return c
}

/** JWKS publique des clés nommées « a+b » (deux clés pendant une rotation). */
export function jwks(noms: string): string {
  return JSON.stringify({ keys: noms.split('+').map((n) => cleNommee(n).publique) })
}

/** Demande de jeton d'une application avec la clé nommée ; rend le statut et, en cas de succès, le jeton. */
export async function demanderJeton(serveur: string, client: string, cle: string): Promise<{ statut: number; jeton?: string }> {
  const r = await fetch(`${serveur}/oauth2/token`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
    body: new URLSearchParams({ grant_type: 'client_credentials', client_id: client,
      client_assertion_type: 'urn:ietf:params:oauth:client-assertion-type:jwt-bearer',
      client_assertion: assertion({ clientId: client, organisation: '', cle: cleNommee(cle).privee }, requis('VINASIGN_JETONS_EMETTEUR')) }),
  })
  return r.status === 200 ? { statut: 200, jeton: (await r.json()).access_token } : { statut: r.status }
}

/** Chaque cas commence avec un trousseau vide : une clé n'appartient qu'à une application (VS-011). */
export function viderTrousseau(): void {
  trousseau.clear()
}
