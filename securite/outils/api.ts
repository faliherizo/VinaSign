import { APIRequestContext, request as fabrique } from '@playwright/test'
import publique from '../donnees/surface-publique.json' with { type: 'json' }

/** Outils communs : inventaire OpenAPI, surface publique déclarée, appels. Aucune route n'est écrite ici. */

export const BASE = process.env.SECURITE_API_URL ?? `http://localhost:${process.env.VINASIGN_PORT ?? 8300}`

export type Operation = { methode: string; chemin: string; parametres: string[]; requete: string[]; corps: boolean }

export async function anonyme(): Promise<APIRequestContext> {
  return fabrique.newContext({ baseURL: BASE })
}

/** Toutes les opérations de l'API, lues dans la spécification OpenAPI publiée par le serveur. */
export async function inventaire(): Promise<Operation[]> {
  const ctx = await anonyme()
  const spec = await (await ctx.get('/v3/api-docs')).json()
  const operations: Operation[] = []
  for (const [chemin, item] of Object.entries<Record<string, any>>(spec.paths ?? {})) {
    for (const [methode, op] of Object.entries<any>(item)) {
      const params = (op.parameters ?? []) as { in: string; name: string }[]
      operations.push({
        methode: methode.toUpperCase(),
        chemin,
        parametres: params.filter((p) => p.in === 'path').map((p) => p.name),
        requete: params.filter((p) => p.in === 'query').map((p) => p.name),
        corps: Boolean(op.requestBody),
      })
    }
  }
  return operations
}

export const cle = (op: Operation) => `${op.methode} ${op.chemin}`

/** Vrai si l'opération figure, motivée, dans donnees/surface-publique.json. */
export function estPublique(op: Operation): boolean {
  return publique.operations.some((p) => new RegExp(p.motif).test(cle(op)))
}

/** Chemin concret : chaque paramètre reçoit sa valeur, ou la valeur générique « * », ou un code plausible. */
export function remplir(op: Operation, valeurs: Record<string, string> = {}): string {
  return op.chemin.replace(/\{([^}]+)\}/g, (_, nom) => encodeURIComponent(valeurs[nom] ?? valeurs['*'] ?? 'essai'))
}

export async function appeler(ctx: APIRequestContext, methode: string, url: string, corps?: unknown) {
  const r = await ctx.fetch(url, { method: methode, data: corps, failOnStatusCode: false, maxRedirects: 0 })
  return { statut: r.status(), texte: await r.text().catch(() => ''), entetes: r.headers() }
}
