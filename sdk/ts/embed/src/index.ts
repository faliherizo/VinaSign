/**
 * @vinasign/embed (VS-055) : affiche la session intégrée d'un signataire dans la page de l'application, et lui remonte
 * les avis de l'interface. Sans dépendance ni règle de sécurité : VinaSign n'accepte l'iframe que sur les origines
 * déclarées par l'application (frame-ancestors) et n'écrit qu'à elles ; ce module, lui, n'écoute que l'origine de
 * VinaSign et que l'iframe qu'il a créée.
 */

/** Avis émis par l'interface du signataire ; aucun ne porte de donnée personnelle. */
export type AvisVinaSign = 'ready' | 'signed' | 'declined' | 'error' | 'closed'

const AVIS: ReadonlySet<string> = new Set(['ready', 'signed', 'declined', 'error', 'closed'])

export interface OptionsIntegration {
  /** URL rendue par POST /api/v1/envelopes/{id}/sessions (5 minutes, usage unique). */
  url: string
  /** Élément qui recevra l'iframe. */
  conteneur: HTMLElement
  /** Titre accessible de l'iframe, dans la langue de la page (WCAG 4.1.2). */
  titre: string
  surAvis?: (avis: AvisVinaSign) => void
}

export interface Integration {
  readonly iframe: HTMLIFrameElement
  /** Retire l'iframe et cesse d'écouter. */
  retirer(): void
}

export class UrlIntegrationInvalide extends Error {}

/** Origine de VinaSign tirée de l'URL de session : https exigé, sauf sur le poste du développeur. */
export function origineVinaSign(url: string): string {
  let u: URL
  try {
    u = new URL(url)
  } catch {
    throw new UrlIntegrationInvalide('URL de session intégrée illisible.')
  }
  const poste = u.protocol === 'http:' && ['localhost', '127.0.0.1'].includes(u.hostname)
  if ((u.protocol !== 'https:' && !poste) || !/^\/integre\/[A-Za-z0-9_-]+$/.test(u.pathname)) {
    throw new UrlIntegrationInvalide('URL de session intégrée attendue : https://…/integre/{jeton}.')
  }
  return u.origin
}

export function integrer(options: OptionsIntegration): Integration {
  const origine = origineVinaSign(options.url)
  if (!options.titre.trim()) {
    throw new UrlIntegrationInvalide("Un titre accessible est requis pour l'iframe.")
  }
  const doc = options.conteneur.ownerDocument
  const iframe = doc.createElement('iframe')
  iframe.src = options.url
  iframe.title = options.titre
  // L'interface du signataire lit l'origine de la page parente quand ancestorOrigins manque (Firefox).
  iframe.referrerPolicy = 'origin'
  iframe.allow = ''
  iframe.style.border = '0'
  iframe.style.width = '100%'
  iframe.style.minHeight = '80vh'
  const ecouter = (e: MessageEvent): void => {
    const donnees = e.data as { source?: unknown; avis?: unknown } | null
    if (e.origin !== origine || e.source !== iframe.contentWindow || donnees?.source !== 'vinasign' || !AVIS.has(String(donnees.avis))) {
      return
    }
    options.surAvis?.(donnees.avis as AvisVinaSign)
  }
  const fenetre = doc.defaultView!
  fenetre.addEventListener('message', ecouter)
  options.conteneur.appendChild(iframe)
  return {
    iframe,
    retirer() {
      fenetre.removeEventListener('message', ecouter)
      iframe.remove()
    },
  }
}
