import { ApplicationConfig, provideBrowserGlobalErrorListeners, provideZoneChangeDetection } from '@angular/core';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { provideRouter, withComponentInputBinding } from '@angular/router';
import { CONFIGURATION, ConfigurationVinaSign } from '@vinasign/commun';
import { routes } from './app.routes';
import { intercepteurSession } from './api/session-signataire';
import { ChargeurPdf, ChargeurPdfJs } from '@vinasign/commun';

/** Fournisseurs de l'application ; la configuration lue à l'exécution est injectée partout. */
export function configurationApplication(configuration: ConfigurationVinaSign): ApplicationConfig {
  return {
    providers: [
      provideBrowserGlobalErrorListeners(),
      provideZoneChangeDetection({ eventCoalescing: true }),
      provideRouter(routes, withComponentInputBinding()),
      provideHttpClient(withInterceptors([intercepteurSession])),
      { provide: ChargeurPdf, useClass: ChargeurPdfJs },
      { provide: CONFIGURATION, useValue: configuration },
    ],
  };
}
