import { ApplicationConfig, inject, provideAppInitializer, provideBrowserGlobalErrorListeners, provideZoneChangeDetection } from '@angular/core';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { provideRouter, withComponentInputBinding } from '@angular/router';
import { CONFIGURATION, ChargeurPdf, ChargeurPdfJs, ConfigurationVinaSign } from '@vinasign/commun';
import { routes } from './app.routes';
import { CLIENT_OIDC, ClientOidc, creerClientOidc } from './connexion/client-oidc';
import { jetonIntercepteur } from './connexion/jeton.intercepteur';
import { SessionPortail } from './connexion/session-portail';

/** Termine une connexion revenue du fournisseur avant la première navigation, puis rouvre l'écran demandé. */
export async function demarrerSession(session: SessionPortail, adresse: Location = window.location, historique: History = history): Promise<void> {
  const retour = await session.demarrer(adresse.href);
  if (retour !== null) {
    historique.replaceState(null, '', retour);
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
      provideHttpClient(withInterceptors([jetonIntercepteur])),
      { provide: CONFIGURATION, useValue: configuration },
      { provide: ChargeurPdf, useClass: ChargeurPdfJs },
      { provide: CLIENT_OIDC, useValue: client },
      provideAppInitializer(() => demarrerSession(inject(SessionPortail))),
    ],
  };
}
