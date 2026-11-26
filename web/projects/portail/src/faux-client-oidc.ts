import { ClientOidc, UtilisateurOidc } from './app/connexion/client-oidc';

/** Client OIDC de test : enregistre les appels et déclenche les événements du fournisseur à la demande. */
export class FauxClientOidc implements ClientOidc {
  readonly appels: string[] = [];
  silencieux: () => Promise<UtilisateurOidc | null> = () => Promise.reject(new Error('login_required'));
  retour: () => Promise<UtilisateurOidc> = () => Promise.reject(new Error('aucun retour'));
  deconnexion: () => Promise<void> = () => Promise.resolve();
  private charge?: (u: UtilisateurOidc) => void;
  private decharge?: () => void;
  private expire?: () => void;

  readonly events = {
    addUserLoaded: (r: (u: UtilisateurOidc) => void) => (this.charge = r),
    addUserUnloaded: (r: () => void) => (this.decharge = r),
    addAccessTokenExpired: (r: () => void) => (this.expire = r),
  };

  signinRedirect(args: { url_state: string }): Promise<void> {
    this.appels.push('connexion ' + args.url_state);
    return Promise.resolve();
  }

  signinRedirectCallback(url: string): Promise<UtilisateurOidc> {
    this.appels.push('retour ' + url);
    return this.retour();
  }

  signinSilent(): Promise<UtilisateurOidc | null> {
    this.appels.push('silencieuse');
    return this.silencieux();
  }

  signoutRedirect(): Promise<void> {
    this.appels.push('deconnexion');
    return this.deconnexion();
  }

  removeUser(): Promise<void> {
    this.appels.push('oubli');
    return Promise.resolve();
  }

  chargerUtilisateur(u: UtilisateurOidc): void { this.charge?.(u); }
  dechargerUtilisateur(): void { this.decharge?.(); }
  expirerJeton(): void { this.expire?.(); }
}

export function utilisateur(roles: unknown, profil: Record<string, unknown> = { name: 'Alix Tremblay' }, url_state?: string): UtilisateurOidc {
  return { access_token: 'jeton-' + Math.random().toString(36).slice(2), profile: { ...profil, roles }, url_state };
}
