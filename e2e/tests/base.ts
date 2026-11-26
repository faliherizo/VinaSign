import pg from 'pg'

/**
 * Accès direct à la base de développement, pour éprouver ce qu'aucune API n'expose encore (ajout d'un référentiel par
 * l'administration, VS-014). Identifiants tirés de l'environnement (.env), jamais écrits ici.
 */
export async function base(): Promise<pg.Client> {
  const requis = (nom: string) => {
    const v = process.env[nom]
    if (!v) throw new Error(`Variable ${nom} absente : charger .env (set -a; . ../.env; set +a).`)
    return v
  }
  const client = new pg.Client({
    host: process.env.VINASIGN_DB_HOTE ?? 'localhost',
    port: Number(process.env.VINASIGN_DB_PORT ?? 55432),
    database: requis('VINASIGN_DB_NOM'),
    user: requis('VINASIGN_DB_UTILISATEUR'),
    password: requis('VINASIGN_DB_MDP'),
  })
  await client.connect()
  return client
}
