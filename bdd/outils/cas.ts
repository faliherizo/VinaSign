import { APIRequestContext, Playwright, expect } from '@playwright/test'
import { createHmac } from 'crypto'
import { readFileSync } from 'fs'
import { dirname, join, resolve } from 'path'
import { fileURLToPath } from 'url'
import { comptes } from './donnees'
import { administrateurPlateforme, attestation, demanderJeton, enregistrer, jeton, jetonPortail, jwks, viderTrousseau, type ApplicationEssai } from './application'

/** Serveur VinaSign (le point de jeton n'est pas relayé par le portail, qui ne relaie que /api). */
export function serveur(): string {
  return process.env.BDD_SERVEUR_URL ?? `http://localhost:${process.env.VINASIGN_PORT ?? 8300}`
}

/**
 * Moteur des cas exécutables décrits en JSON (bdd/donnees/*.json). Un cas est une suite d'appels réels à la
 * plateforme, chacun avec son statut attendu, ses captures (réutilisées par les appels suivants sous la forme
 * ${variable}) et ses vérifications sur la réponse. Aucune logique d'exigence n'est codée ici : elle vit dans les
 * données, relues et tenues à jour avec les exigences.
 */

export type Verification =
  | { chemin: string; egal: unknown }
  | { chemin: string; contient: string }
  | { chemin: string; inclut: unknown }
  | { chemin: string; exclut: unknown }
  | { chemin: string; existe: boolean }
  | { chemin: string; longueurMin: number }
  | { chemin: string; different: unknown }
  | { chemin: string; egalTexte: string }
  | { texte: string }
  | { texteAbsent: string }
  | { texteContient: string }

export type Fichier = { fichier: string; nom: string; type?: string }

/** Lecture d'un courriel reçu par SMTP (boîte Mailpit du bac à sable), comme le ferait le destinataire. */
/** `different` : ignore une valeur déjà vue (ex. l'ancien lien), pour attendre le courriel suivant. */
export type LectureCourriel = { destinataire: string; motif: string; capturer: string; different?: string }

export type Etape = {
  appel?: string
  courriel?: LectureCourriel
  profil?: string
  /** Demande de jeton d'une application avec une clé du trousseau ; capture le jeton s'il est émis. */
  jeton?: { client: string; cle: string; statut?: number; capturer?: string }
  /** Traitement asynchrone (finalisation, webhooks) : l'appel est répété jusqu'à n secondes tant que statut ou vérifications échouent. */
  attendre?: number
  /** Attestation (VS-044) d'un utilisateur, signée par l'application du profil ; capturée dans une variable. */
  attestation?: { sujet: string; amr: string[]; authIlYa?: number; capturer: string }
  /** Utilisateur du portail désigné par son courriel (invité plus tôt dans le cas), jeton du fournisseur OIDC. */
  personne?: string
  /** Avec {@code personne} : jeton sans authentification multifacteur. */
  sansMfa?: boolean
  /** Appel sans session (connexion, portail public) : n'altère aucune session de profil. */
  anonyme?: boolean
  corps?: unknown
  multipart?: Record<string, string | Fichier>
  /**
   * Corps binaire : un PDF minimal valide, un texte présenté sous un type donné (dépôt par URL présignée), un fichier
   * conservé plus tôt dans le cas (`fichier:<nom>`), ou ce fichier altéré d'un octet (`fichier-altere:<nom>`).
   */
  binaire?: { contenu: 'pdf-minimal' | 'texte' | `fichier:${string}` | `fichier-altere:${string}`; type: string }
  /** Conserve le corps brut de la réponse sous ce nom (ex. le document final scellé), pour le soumettre ensuite. */
  conserver?: string
  /** Corps application/x-www-form-urlencoded (ex. jeton OAuth 2.0). */
  formulaire?: Record<string, string>
  entetes?: Record<string, string>
  statut?: number | number[]
  capturer?: Record<string, string>
  verifier?: Verification[]
  note?: string
}

export type Cas = { description: string; profil?: string; etapes: Etape[] }

export type Contexte = {
  variables: Record<string, unknown>
  ecarts: string[]
  derniere?: { statut: number; corps: unknown; texte: string }
  /** Fichiers conservés par les étapes `conserver` du scénario. */
  fichiers?: Record<string, Buffer>
}

/**
 * Octet modifié dans la ligne de commentaire binaire qui suit l'en-tête d'un PDF : le fichier reste lisible, mais le
 * contenu couvert par la signature n'est plus le même.
 */
const OCTET_ALTERE = 11

function corpsBinaire(contenu: string, ctx: Contexte, ou: string): Buffer {
  if (contenu === 'pdf-minimal') return PDF_MINIMAL
  if (contenu === 'texte') return Buffer.from('Ceci n\'est pas un PDF', 'utf8')
  const [genre, nom] = contenu.split(':')
  const f = ctx.fichiers?.[nom]
  if (!f) throw new Error(`${ou} : aucun fichier conservé sous « ${nom} » (étape « conserver »)`)
  if (genre === 'fichier') return f
  const altere = Buffer.from(f)
  altere[OCTET_ALTERE] ^= 0x01
  return altere
}

const RACINE = resolve(dirname(fileURLToPath(import.meta.url)), '..')

/** PDF minimal d'une page, reconnu par le contrôle de format de la plateforme. */
export const PDF_MINIMAL = Buffer.from('%PDF-1.4\n1 0 obj<</Type/Catalog/Pages 2 0 R>>endobj\n2 0 obj<</Type/Pages/Kids[3 0 R]/Count 1>>endobj\n'
  + '3 0 obj<</Type/Page/Parent 2 0 R/MediaBox[0 0 612 792]>>endobj\ntrailer<</Root 1 0 R>>\n%%EOF\n', 'latin1')

const sessions = new Map<string, APIRequestContext>()
/** Application enregistrée pour chaque profil « application » : sa clé signe aussi les attestations. */
const applicationsDesProfils = new Map<string, ApplicationEssai>()

/** Contexte d'API authentifié par profil, réutilisé pendant toute l'exécution. */
export async function session(playwright: Playwright, baseURL: string, profil: string): Promise<APIRequestContext> {
  const cle = `${baseURL}|${profil}`
  const existante = sessions.get(cle)
  if (existante) return existante
  const compte = comptes[profil]
  if (!compte) throw new Error(`Profil inconnu dans donnees/comptes.json : « ${profil} »`)
  // Un mode d'authentification par profil : « anonyme », ou « application » (client credentials, private_key_jwt,
  // VS-012) : application enregistrée pour l'exécution, jeton obtenu au point de jeton du serveur.
  let c: APIRequestContext
  if (compte.mode === 'anonyme') {
    c = await playwright.request.newContext({ baseURL })
  } else if (compte.mode === 'application') {
    const portees = compte.portees ?? []
    const app = await enregistrer(portees)
    applicationsDesProfils.set(cle, app)
    const acces = await jeton(serveur(), app, portees)
    c = await playwright.request.newContext({ baseURL, extraHTTPHeaders: { Authorization: `Bearer ${acces}` } })
  } else if (compte.mode === 'plateforme') {
    const acces = await jetonPortail(await administrateurPlateforme(), true)
    c = await playwright.request.newContext({ baseURL, extraHTTPHeaders: { Authorization: `Bearer ${acces}` } })
  } else {
    throw new Error(`Mode d'authentification non pris en charge : « ${compte.mode} » (profil « ${profil} »)`)
  }
  sessions.set(cle, c)
  return c
}

export function oublierSession(baseURL: string, profil: string) {
  sessions.delete(`${baseURL}|${profil}`)
}

/** Code TOTP (RFC 6238 : HMAC-SHA1, 30 s, 6 chiffres) du secret base32, comme une application d'authentification. */
export function totp(secret: string, instant = Date.now()): string {
  const alphabet = 'ABCDEFGHIJKLMNOPQRSTUVWXYZ234567'
  let bits = ''
  for (const c of secret.replace(/=+$/, '').toUpperCase()) bits += alphabet.indexOf(c).toString(2).padStart(5, '0')
  const cle = Buffer.from(bits.match(/.{8}/g)!.map((b) => parseInt(b, 2)))
  const compteur = Buffer.alloc(8)
  compteur.writeBigUInt64BE(BigInt(Math.floor(instant / 30_000)))
  const h = createHmac('sha1', cle).update(compteur).digest()
  const o = h[h.length - 1] & 0xf
  return String((h.readUInt32BE(o) & 0x7fffffff) % 1_000_000).padStart(6, '0')
}

/** Remplace ${variable} par sa valeur ; une chaîne réduite à « ${x} » garde le type de la valeur (nombre, objet…). */
export function substituer<T>(valeur: T, variables: Record<string, unknown>): T {
  if (typeof valeur === 'string') {
    const seule = valeur.match(/^\$\{([\w.-]+)\}$/)
    if (seule && seule[1] in variables) return variables[seule[1]] as T
    return valeur.replace(/\$\{jwks:([\w+]+)\}/g, (_, noms) => jwks(noms))
      .replace(/\$\{futur:(\d+)\}/g, (_, s) => new Date(Date.now() + Number(s) * 1000).toISOString())
      .replace(/\$\{totp:([A-Z2-7]+)\}/g, (_, secret) => totp(secret)).replace(/\$\{([\w.-]+)\}/g, (_, nom) => {
      if (!(nom in variables)) throw new Error(`Variable non capturée : \${${nom}}`)
      return String(variables[nom])
    }) as T
  }
  if (Array.isArray(valeur)) return valeur.map((v) => substituer(v, variables)) as T
  if (valeur && typeof valeur === 'object') {
    return Object.fromEntries(Object.entries(valeur).map(([k, v]) => [k, substituer(v, variables)])) as T
  }
  return valeur
}

/** Chemin JSON simple : $.a.b[0].c, $[1], $.a[*].b (projection sur un tableau), $[?slug=x].id (premier élément filtré). */
export function lire(objet: unknown, chemin: string): unknown {
  const parties = chemin.replace(/^\$\.?/, '').match(/\[\?[^\]]+\]|[^.[\]]+|\[\d+\]|\[\*\]/g) ?? []
  let courant: unknown = objet
  for (let i = 0; i < parties.length; i++) {
    const p = parties[i]
    if (courant === undefined || courant === null) return undefined
    if (p === '[*]') {
      const reste = '$' + parties.slice(i + 1).map((x) => (x.startsWith('[') ? x : '.' + x)).join('')
      return (courant as unknown[]).map((e) => (reste === '$' ? e : lire(e, reste)))
    }
    const filtre = p.match(/^\[\?([\w.-]+)=(.+)\]$/)
    if (filtre) {
      courant = (courant as Record<string, unknown>[]).find((e) => String(e?.[filtre[1]]) === filtre[2])
      continue
    }
    const index = p.match(/^\[(\d+)\]$/)
    courant = index ? (courant as unknown[])[Number(index[1])] : (courant as Record<string, unknown>)[p]
  }
  return courant
}

function verifier(v: Verification, corps: unknown, texte: string): string | null {
  if ('texte' in v) return texte.includes(v.texte) ? null : `la réponse devrait contenir « ${v.texte} »`
  if ('texteAbsent' in v) return texte.includes(v.texteAbsent) ? `la réponse ne devrait pas contenir « ${v.texteAbsent} »` : null
  if ('texteContient' in v) return texte.includes(v.texteContient) ? null : `la réponse devrait contenir « ${v.texteContient} »`
  const valeur = lire(corps, v.chemin)
  const vue = JSON.stringify(valeur)?.slice(0, 200)
  if ('egal' in v) return JSON.stringify(valeur) === JSON.stringify(v.egal) ? null : `${v.chemin} = ${vue}, attendu ${JSON.stringify(v.egal)}`
  if ('egalTexte' in v) return String(valeur) === String(v.egalTexte) ? null : `${v.chemin} = ${vue}, attendu « ${v.egalTexte} »`
  if ('different' in v) return JSON.stringify(valeur) !== JSON.stringify(v.different) ? null : `${v.chemin} ne devrait pas valoir ${vue}`
  if ('contient' in v) return JSON.stringify(valeur ?? '').includes(v.contient) ? null : `${v.chemin} = ${vue}, devrait contenir « ${v.contient} »`
  if ('inclut' in v) {
    const liste = Array.isArray(valeur) ? valeur.flat(Infinity) : []
    return liste.some((e) => JSON.stringify(e) === JSON.stringify(v.inclut)) ? null : `${v.chemin} = ${vue}, devrait inclure ${JSON.stringify(v.inclut)}`
  }
  if ('exclut' in v) {
    const liste = Array.isArray(valeur) ? valeur.flat(Infinity) : []
    return liste.some((e) => JSON.stringify(e) === JSON.stringify(v.exclut)) ? `${v.chemin} = ${vue}, ne devrait pas inclure ${JSON.stringify(v.exclut)}` : null
  }
  if ('existe' in v) return (valeur !== undefined && valeur !== null) === v.existe ? null : `${v.chemin} ${v.existe ? 'absent' : 'présent'} (${vue})`
  if ('longueurMin' in v) {
    const n = Array.isArray(valeur) || typeof valeur === 'string' ? valeur.length : -1
    return n >= v.longueurMin ? null : `${v.chemin} : longueur ${n}, au moins ${v.longueurMin} attendu`
  }
  return `vérification inconnue : ${JSON.stringify(v)}`
}

function contenuFichier(f: Fichier): Buffer {
  if (f.fichier === 'pdf') return PDF_MINIMAL
  if (f.fichier.startsWith('pdf:')) {
    // PDF de la taille demandée : en-tête valide, puis remplissage en commentaire (fichier volumineux réaliste).
    const taille = Number(f.fichier.slice(4))
    return Buffer.concat([PDF_MINIMAL, Buffer.alloc(Math.max(0, taille - PDF_MINIMAL.length), '%')])
  }
  if (f.fichier.startsWith('texte:')) return Buffer.from(f.fichier.slice(6), 'utf8')
  if (f.fichier.startsWith('chemin:')) return readFileSync(join(RACINE, f.fichier.slice(7)))
  throw new Error(`Fichier inconnu : ${f.fichier} (pdf, texte:…, chemin:…)`)
}

const BOITE = process.env.BDD_COURRIEL_URL ?? `http://localhost:${process.env.VINASIGN_MAILPIT_PORT ?? 28025}/api/v1`

/** Attend le plus récent courriel du destinataire qui contient le motif (expression régulière à un groupe). */
async function lireCourriel(playwright: Playwright, l: LectureCourriel, ou: string): Promise<string> {
  const api = await playwright.request.newContext()
  const motif = new RegExp(l.motif)
  for (let essai = 0; essai < 30; essai++) {
    const r = await api.get(`${BOITE}/search?query=${encodeURIComponent(`to:${l.destinataire}`)}`).catch(() => null)
    expect(r?.ok(), `${ou} : boîte courriel du bac à sable injoignable (${BOITE})`).toBeTruthy()
    for (const m of ((await r!.json()).messages ?? []).slice(0, 5)) {
      const texte = (await (await api.get(`${BOITE}/message/${m.ID}`)).json()).Text as string
      const trouve = texte.match(motif)
      if (trouve && trouve[1] !== l.different) return trouve[1]
    }
    await new Promise((ok) => setTimeout(ok, 1_000))
  }
  throw new Error(`${ou} : aucun courriel pour ${l.destinataire} correspondant à /${l.motif}/`)
}

/** Exécute un cas ; un statut inattendu arrête le cas, les vérifications de contenu sont toutes relevées. */
export async function executer(playwright: Playwright, baseURL: string, profilParDefaut: string, nom: string, cas: Cas, ctx: Contexte) {
  viderTrousseau()
  for (const [i, brute] of cas.etapes.entries()) {
    const e = substituer(brute, ctx.variables)
    if (e.courriel) {
      ctx.variables[e.courriel.capturer] = await lireCourriel(playwright, e.courriel, `cas « ${nom} », étape ${i + 1}`)
      continue
    }
    if (e.attestation) {
      const profil = e.profil ?? cas.profil ?? profilParDefaut
      await session(playwright, baseURL, profil)
      const app = applicationsDesProfils.get(`${baseURL}|${profil}`)
      if (!app) throw new Error(`cas « ${nom} », étape ${i + 1} : le profil « ${profil} » n'est pas une application`)
      ctx.variables[e.attestation.capturer] = attestation(app, e.attestation.sujet, e.attestation.amr, e.attestation.authIlYa ?? 0)
      continue
    }
    if (e.jeton) {
      const r = await demanderJeton(serveur(), e.jeton.client, e.jeton.cle)
      expect(r.statut, `cas « ${nom} », étape ${i + 1} : jeton de ${e.jeton.client} avec la clé ${e.jeton.cle}`).toBe(e.jeton.statut ?? 200)
      if (e.jeton.capturer && r.jeton) ctx.variables[e.jeton.capturer] = r.jeton
      continue
    }
    const [methode, chemin] = e.appel!.split(' ')
    const api = e.anonyme ? await playwright.request.newContext({ baseURL })
      : e.personne ? await playwright.request.newContext({ baseURL, extraHTTPHeaders: { Authorization: `Bearer ${await jetonPortail(e.personne, !e.sansMfa)}` } })
      : await session(playwright, baseURL, e.profil ?? cas.profil ?? profilParDefaut)
    const options: Parameters<APIRequestContext['fetch']>[1] = { method: methode, headers: e.entetes, failOnStatusCode: false }
    if (e.multipart) {
      options.multipart = Object.fromEntries(Object.entries(e.multipart).map(([k, v]) => [k, typeof v === 'string' ? v
        : { name: v.nom, mimeType: v.type ?? 'application/octet-stream', buffer: contenuFichier(v) }]))
    } else if (e.binaire) {
      options.data = corpsBinaire(e.binaire.contenu, ctx, `cas « ${nom} », étape ${i + 1}`)
      options.headers = { ...(e.entetes ?? {}), 'Content-Type': e.binaire.type }
    } else if (e.formulaire) {
      options.form = e.formulaire
    } else if (e.corps !== undefined) {
      options.data = e.corps
    }
    const attendus = e.statut === undefined ? [200, 201, 204] : Array.isArray(e.statut) ? e.statut : [e.statut]
    const echeance = Date.now() + (e.attendre ?? 0) * 1000
    let r = await api.fetch(chemin, options)
    let texte = await r.text()
    let corps: unknown = texte
    for (;;) {
      corps = texte
      try { corps = texte ? JSON.parse(texte) : null } catch { /* réponse non JSON : texte brut */ }
      const pret = attendus.includes(r.status()) && (e.verifier ?? []).every((v) => !verifier(v, corps, texte))
      if (pret || Date.now() >= echeance) break
      await new Promise((ok) => setTimeout(ok, 1_000))
      r = await api.fetch(chemin, options)
      texte = await r.text()
    }
    ctx.derniere = { statut: r.status(), corps, texte }
    if (e.conserver) ctx.fichiers = { ...ctx.fichiers, [e.conserver]: await r.body() }
    expect(attendus, `cas « ${nom} », étape ${i + 1} (${e.appel}) : statut ${r.status()} — ${texte.slice(0, 300)}`).toContain(r.status())
    for (const [variable, capture] of Object.entries(e.capturer ?? {})) {
      // « $.chemin ~ motif » : la valeur lue est filtrée par une expression régulière (premier groupe).
      const [cheminJson, motif] = capture.split(' ~ ')
      const brute = lire(corps, cheminJson)
      const valeur = motif && typeof brute === 'string' ? brute.match(new RegExp(motif))?.[1] : brute
      if (valeur === undefined) ctx.ecarts.push(`cas « ${nom} », étape ${i + 1} : capture ${variable} ← ${cheminJson} introuvable`)
      ctx.variables[variable] = valeur
    }
    for (const v of e.verifier ?? []) {
      const ecart = verifier(v, corps, texte)
      if (ecart) ctx.ecarts.push(`cas « ${nom} », étape ${i + 1} (${e.appel}) : ${ecart}`)
    }
  }
}
