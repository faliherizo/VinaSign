import { ApplicationConfig, inject, provideAppInitializer, provideBrowserGlobalErrorListeners, provideZoneChangeDetection } from '@angular/core';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { TitleStrategy, provideRouter, withComponentInputBinding } from '@angular/router';
import { CONFIGURATION, ChargeurPdf, ChargeurPdfJs, ConfigurationVinaSign, TitreTraduit } from '@vinasign/commun';
import { ROLES, routes } from './app.routes';
import { CLIENT_OIDC, ClientOidc, creerClientOidc } from './connexion/client-oidc';
import { jetonIntercepteur } from './connexion/jeton.intercepteur';
import { SessionPortail } from './connexion/session-portail';

/**
 * Écran de travail de la personne qui vient de se connecter, selon ses rôles ; null sans rôle utile au portail (la vitrine
 * reste alors affichée). Les enveloppes d'abord : c'est l'usage courant ; la facturation pour l'administrateur seul.
 */
export function ecranDeTravail(session: SessionPortail): string | null {
  if (session.aLeRole(ROLES.preparateur) || session.aLeRole(ROLES.auditeur)) {
    return '/enveloppes';
  }
  return session.aLeRole(ROLES.administrateur) ? '/facturation' : null;
}

/**
 * Termine une connexion revenue du fournisseur avant la première navigation, puis rouvre l'écran demandé. Une connexion
 * lancée depuis la vitrine n'y ramène pas : elle ouvre l'écran de travail de la personne.
 */
export async function demarrerSession(session: SessionPortail, adresse: Location = window.location, historique: History = history): Promise<void> {
  const retour = await session.demarrer(adresse.href);
  if (retour !== null) {
    historique.replaceState(null, '', retour === '/' ? ecranDeTravail(session) ?? '/' : retour);
  }
}

/** Fournisseurs de l'application ; la configuration lue à l'exécution est injectée partout. */
export function configurationApplication(configuration: ConfigurationVinaSign,
                                         client: ClientOidc | null = configuration.connexion ? creerClientOidc(configuration.connexion, window.location.origin) : null): ApplicationConfig {
  return {
    providers: [
      provideBrowserGlobalErrorListeners(),
      provideZoneChangeDetection({ eventCoalescing: true }),
      provideRouter(routes, withComponentInputBinding()),
      { provide: TitleStrategy, useExisting: TitreTraduit },
      provideHttpClient(withInterceptors([jetonIntercepteur])),
      { provide: CONFIGURATION, useValue: configuration },
      { provide: ChargeurPdf, useClass: ChargeurPdfJs },
      { provide: CLIENT_OIDC, useValue: client },
      provideAppInitializer(() => demarrerSession(inject(SessionPortail))),
    ],
  };
}
