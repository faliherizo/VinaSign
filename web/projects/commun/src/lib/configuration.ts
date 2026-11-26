import { InjectionToken } from '@angular/core';

/**
 * Connexion OIDC du portail (VS-ADR-009) : émetteur, client public et portées demandées. Aucun secret : le client est
 * public, protégé par PKCE.
 */
export interface ConfigurationConnexion {
  readonly emetteur: string;
  readonly client: string;
  readonly portees: string;
}

/** Réglages de l'interface, lus à l'exécution dans /config.json : aucune URL ni langue figée dans le code livré. */
export interface ConfigurationVinaSign {
  /** Racine de l'API ; vide = même origine (relais du serveur de développement ou du frontal). */
  readonly apiUrl: string;
  /** Langue de repli tant que l'utilisateur n'a rien choisi et que le navigateur ne propose rien d'offert. */
  readonly languePrincipale: string;
  /** Code du référentiel qui liste les langues offertes. */
  readonly referentielLangue: string;
  /** Fournisseur d'identité du portail ; absent, le portail n'offre que ses pages publiques. */
  readonly connexion?: ConfigurationConnexion;
}

export const CONFIGURATION = new InjectionToken<ConfigurationVinaSign>('CONFIGURATION_VINASIGN');

/** Charge /config.json avant le démarrage ; une configuration absente ou incomplète empêche de démarrer. */
export async function chargerConfiguration(lecteur: typeof fetch = fetch): Promise<ConfigurationVinaSign> {
  const reponse = await lecteur('config.json', { cache: 'no-store' });
  if (!reponse.ok) {
    throw new Error(`Configuration introuvable (${reponse.status}).`);
  }
  const brut = (await reponse.json()) as Partial<ConfigurationVinaSign>;
  if (typeof brut.apiUrl !== 'string' || !brut.languePrincipale || !brut.referentielLangue) {
    throw new Error('Configuration incomplète : apiUrl, languePrincipale et referentielLangue sont requis.');
  }
  const configuration = { apiUrl: brut.apiUrl.replace(/\/$/, ''), languePrincipale: brut.languePrincipale, referentielLangue: brut.referentielLangue };
  return brut.connexion === undefined ? configuration : { ...configuration, connexion: connexionValide(brut.connexion) };
}

function connexionValide(c: Partial<ConfigurationConnexion>): ConfigurationConnexion {
  if (!c.emetteur || !/^https?:\/\/[^/]/.test(c.emetteur) || !c.client || !c.portees || !c.portees.split(' ').includes('openid')) {
    throw new Error('Configuration de connexion incomplète : emetteur (URL), client et portees (avec openid) sont requis.');
  }
  return { emetteur: c.emetteur.replace(/\/$/, ''), client: c.client, portees: c.portees };
}
