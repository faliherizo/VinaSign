import { InjectionToken } from '@angular/core';
import { InMemoryWebStorage, UserManager, WebStorageStateStore } from 'oidc-client-ts';
import { ConfigurationConnexion } from '@vinasign/commun';

/** Utilisateur rendu par le fournisseur : jeton d'accès, profil (revendications du jeton d'identité), état de retour. */
export interface UtilisateurOidc {
  readonly access_token: string;
  readonly profile: Readonly<Record<string, unknown>>;
  readonly url_state?: string;
}

/**
 * Port étroit sur le client OIDC certifié (oidc-client-ts) : ce dont le portail a besoin, rien de plus. Une
 * implémentation de test le remplace sans navigateur ni fournisseur.
 */
export interface ClientOidc {
  signinRedirect(args: { url_state: string }): Promise<void>;
  signinRedirectCallback(url: string): Promise<UtilisateurOidc>;
  signinSilent(): Promise<UtilisateurOidc | null>;
  signoutRedirect(): Promise<void>;
  removeUser(): Promise<void>;
  readonly events: {
    addUserLoaded(rappel: (u: UtilisateurOidc) => void): unknown;
    addUserUnloaded(rappel: () => void): unknown;
    addAccessTokenExpired(rappel: () => void): unknown;
  };
}

/** Client du portail, ou null quand config.json ne déclare aucun fournisseur d'identité. */
export const CLIENT_OIDC = new InjectionToken<ClientOidc | null>('CLIENT_OIDC');

export const CHEMIN_RETOUR = '/connexion/retour';
export const PAGE_SILENCIEUSE = '/connexion-silencieuse.html';

/**
 * Code d'autorisation avec PKCE. Jetons en mémoire seulement (VS-ADR-009) : un rechargement de page les perd et le
 * renouvellement silencieux les retrouve tant que la session du fournisseur dure. L'état transitoire de la redirection
 * (vérificateur PKCE, sans jeton) vit dans le stockage de session de l'onglet.
 */
export function creerClientOidc(c: ConfigurationConnexion, origine: string, etat: Storage = sessionStorage): ClientOidc {
  return new UserManager({
    authority: c.emetteur,
    client_id: c.client,
    scope: c.portees,
    redirect_uri: origine + CHEMIN_RETOUR,
    silent_redirect_uri: origine + PAGE_SILENCIEUSE,
    post_logout_redirect_uri: origine + '/',
    response_type: 'code',
    automaticSilentRenew: true,
    userStore: new WebStorageStateStore({ store: new InMemoryWebStorage() }),
    stateStore: new WebStorageStateStore({ store: etat }),
  });
}
