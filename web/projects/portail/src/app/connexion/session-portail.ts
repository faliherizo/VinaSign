import { HttpBackend, HttpClient, HttpHeaders } from '@angular/common/http';
import { Injectable, InjectionToken, computed, inject, signal } from '@angular/core';
import { CONFIGURATION } from '@vinasign/commun';
import { firstValueFrom, map } from 'rxjs';
import { CHEMIN_RETOUR, CLIENT_OIDC, UtilisateurOidc } from './client-oidc';

/**
 * Rôles de la personne dans VinaSign, lus par GET /api/v1/moi avec son jeton. Ils sont attribués dans VinaSign
 * (VS-014) : l'annuaire de l'organisation ne les connaît pas. L'appel contourne l'intercepteur du jeton, qui dépend
 * lui-même de la session. Null quand le portail n'a pas d'API configurée (écrans publics, tests).
 */
export const LECTURE_ROLES = new InjectionToken<((jeton: string) => Promise<readonly string[]>) | null>('LECTURE_ROLES', {
  providedIn: 'root',
  factory: () => {
    const configuration = inject(CONFIGURATION, { optional: true });
    if (configuration === null) {
      return null;
    }
    const http = new HttpClient(inject(HttpBackend));
    return (jeton: string) => firstValueFrom(http.get<{ roles?: unknown }>(configuration.apiUrl + '/api/v1/moi', {
      headers: new HttpHeaders({ Authorization: 'Bearer ' + jeton }),
    }).pipe(map((m) => liste(m.roles))));
  },
});

/** Personne connectée au portail : nom affiché et rôles que VinaSign lui attribue (à défaut, ceux de son jeton). */
export interface UtilisateurPortail {
  readonly nom: string;
  readonly roles: readonly string[];
}

/**
 * Session du portail (VS-ADR-009). Le serveur décide de tout accès ; les rôles servent seulement à n'offrir que les
 * écrans utiles. Le jeton n'est gardé qu'en mémoire.
 */
@Injectable({ providedIn: 'root' })
export class SessionPortail {
  private readonly client = inject(CLIENT_OIDC, { optional: true }) ?? null;
  private readonly lectureRoles = inject(LECTURE_ROLES);
  private readonly courant = signal<UtilisateurOidc | null>(null);
  private readonly rolesServeur = signal<readonly string[] | null>(null);
  private silencieuse: Promise<void> = Promise.resolve();
  private roles: Promise<void> = Promise.resolve();

  readonly estOfferte = this.client !== null;
  readonly utilisateur = computed<UtilisateurPortail | null>(() => {
    const u = this.courant();
    return u === null ? null : { nom: nomAffiche(u.profile), roles: this.rolesServeur() ?? liste(u.profile['roles']) };
  });

  constructor() {
    this.client?.events.addUserLoaded((u) => this.poser(u));
    this.client?.events.addUserUnloaded(() => this.poser(null));
    this.client?.events.addAccessTokenExpired(() => this.poser(null));
  }

  /** Personne connectée, puis ses rôles lus chez VinaSign ; un échec de lecture laisse ceux du jeton. */
  private poser(u: UtilisateurOidc | null): void {
    this.courant.set(u);
    this.rolesServeur.set(null);
    if (u === null || this.lectureRoles === null) {
      this.roles = Promise.resolve();
      return;
    }
    this.roles = this.lectureRoles(u.access_token).then((r) => {
      if (this.courant() === u) {
        this.rolesServeur.set(r);
      }
    }, () => undefined);
  }

  /**
   * Au démarrage : termine une connexion en cours (retour du fournisseur) et rend le chemin à rouvrir ; sinon tente, sans
   * bloquer, de retrouver la session du fournisseur.
   */
  async demarrer(url: string): Promise<string | null> {
    if (this.client === null) {
      return null;
    }
    if (new URL(url).pathname === CHEMIN_RETOUR) {
      try {
        const u = await this.client.signinRedirectCallback(url);
        this.poser(u);
        await this.roles;
        return cheminInterne(u.url_state);
      } catch {
        return '/';
      }
    }
    this.silencieuse = this.client.signinSilent().then((u) => this.poser(u), () => undefined);
    return null;
  }

  /** Attendu par la garde : la tentative silencieuse du démarrage et la lecture des rôles sont terminées. */
  async pret(): Promise<void> {
    await this.silencieuse;
    await this.roles;
  }

  connecter(retour: string): Promise<void> {
    return this.client === null ? Promise.resolve() : this.client.signinRedirect({ url_state: cheminInterne(retour) });
  }

  async deconnecter(): Promise<void> {
    if (this.client === null) {
      return;
    }
    this.poser(null);
    await this.client.removeUser();
    await this.client.signoutRedirect().catch(() => undefined);
  }

  jeton(): string | null {
    return this.courant()?.access_token ?? null;
  }

  aLeRole(role: string): boolean {
    return this.utilisateur()?.roles.includes(role) ?? false;
  }
}

/** Seul un chemin du portail est rouvert après la connexion : jamais une adresse externe (redirection ouverte). */
function cheminInterne(chemin: string | undefined): string {
  return chemin !== undefined && /^\/(?![/\\])/.test(chemin) ? chemin : '/';
}

function nomAffiche(p: Readonly<Record<string, unknown>>): string {
  for (const cle of ['name', 'preferred_username', 'email']) {
    if (typeof p[cle] === 'string' && p[cle] !== '') {
      return p[cle] as string;
    }
  }
  return '';
}

function liste(r: unknown): readonly string[] {
  return Array.isArray(r) ? r.filter((x): x is string => typeof x === 'string') : [];
}
