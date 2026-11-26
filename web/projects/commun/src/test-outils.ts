import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { EnvironmentProviders, Provider } from '@angular/core';
import { CONFIGURATION, ConfigurationVinaSign } from './lib/configuration';

/** Configuration de test : mêmes champs que config.json, valeurs propres aux tests. */
export const CONFIGURATION_TEST: ConfigurationVinaSign = { apiUrl: 'https://api.essai', languePrincipale: 'fr', referentielLangue: 'langue' };

export function fournisseursDeTest(configuration: ConfigurationVinaSign = CONFIGURATION_TEST): (Provider | EnvironmentProviders)[] {
  return [provideHttpClient(), provideHttpClientTesting(), { provide: CONFIGURATION, useValue: configuration }];
}
