import { TestBed } from '@angular/core/testing';
import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { CONFIGURATION } from '@vinasign/commun';
import { CONFIGURATION_TEST } from '../../../../commun/src/test-outils';
import { CLIENT_OIDC } from './client-oidc';
import { jetonIntercepteur } from './jeton.intercepteur';
import { SessionPortail } from './session-portail';
import { FauxClientOidc, utilisateur } from '../../faux-client-oidc';

describe('jetonIntercepteur', () => {
  let client: FauxClientOidc;
  let http: HttpClient;
  let controle: HttpTestingController;

  beforeEach(() => {
    client = new FauxClientOidc();
    TestBed.configureTestingModule({
      providers: [provideHttpClient(withInterceptors([jetonIntercepteur])), provideHttpClientTesting(),
        { provide: CONFIGURATION, useValue: CONFIGURATION_TEST }, { provide: CLIENT_OIDC, useValue: client }],
    });
    TestBed.inject(SessionPortail);
    http = TestBed.inject(HttpClient);
    controle = TestBed.inject(HttpTestingController);
  });

  it('ajoute le jeton aux appels de l\'API VinaSign seulement', () => {
    const u = utilisateur(['administrateur-organisation']);
    client.chargerUtilisateur(u);
    http.get('https://api.essai/api/v1/billing').subscribe();
    http.get('https://ailleurs.x/api/v1/billing').subscribe();
    http.get('https://api.essai/config.json').subscribe();
    expect(controle.expectOne('https://api.essai/api/v1/billing').request.headers.get('Authorization')).toBe('Bearer ' + u.access_token);
    expect(controle.expectOne('https://ailleurs.x/api/v1/billing').request.headers.has('Authorization')).toBeFalse();
    expect(controle.expectOne('https://api.essai/config.json').request.headers.has('Authorization')).toBeFalse();
  });

  it('sans connexion, la requête part sans jeton', () => {
    http.get('https://api.essai/api/v1/referentiels').subscribe();
    expect(controle.expectOne('https://api.essai/api/v1/referentiels').request.headers.has('Authorization')).toBeFalse();
  });
});
