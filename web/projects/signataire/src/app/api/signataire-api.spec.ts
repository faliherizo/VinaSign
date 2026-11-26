import { TestBed } from '@angular/core/testing';
import { provideHttpClient, withInterceptors, HttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { CONFIGURATION } from '@vinasign/commun';
import { CONFIGURATION_TEST } from '../../../../commun/src/test-outils';
import { SignataireApi } from './signataire-api';
import { intercepteurSession, SessionSignataire } from './session-signataire';
import { typeProbleme } from './modeles';

describe('SignataireApi et session', () => {
  let http: HttpTestingController;
  let api: SignataireApi;
  const base = 'https://api.essai/api/v1/signataire/';

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(withInterceptors([intercepteurSession])), provideHttpClientTesting(), { provide: CONFIGURATION, useValue: CONFIGURATION_TEST }],
    });
    http = TestBed.inject(HttpTestingController);
    api = TestBed.inject(SignataireApi);
  });

  afterEach(() => http.verify());

  it('échange le jeton du lien (dans le corps) contre une session, puis la présente à chaque appel', () => {
    api.ouvrir('jeton-du-lien').subscribe();
    const ouverture = http.expectOne(base + 'ouverture');
    expect(ouverture.request.body).toEqual({ jeton: 'jeton-du-lien' });
    expect(ouverture.request.headers.has('X-Session-Signataire')).toBeFalse();
    ouverture.flush({ jetonSession: 'session-1' });
    expect(TestBed.inject(SessionSignataire).jeton()).toBe('session-1');

    api.consentir('v1').subscribe();
    api.document().subscribe();
    api.champs().subscribe();
    api.signer({ nomSaisi: 'M', valeurs: {} }).subscribe();
    api.refuser(null).subscribe();
    for (const [chemin, corps] of [['consentement', { version: 'v1' }], ['document', null], ['champs', null], ['signature', { nomSaisi: 'M', valeurs: {} }], ['refus', { motif: null }]] as const) {
      const r = http.expectOne(base + chemin);
      expect(r.request.headers.get('X-Session-Signataire')).toBe('session-1');
      expect(r.request.body).toEqual(corps);
      r.flush(chemin === 'document' ? new ArrayBuffer(1) : {});
    }
  });

  it('n\'ajoute pas la session aux autres API', () => {
    TestBed.inject(SessionSignataire).jeton.set('session-1');
    TestBed.inject(HttpClient).get('https://api.essai/api/v1/referentiels/langue').subscribe();
    expect(http.expectOne(() => true).request.headers.has('X-Session-Signataire')).toBeFalse();
  });

  it('lit le type d\'un problème RFC 9457', () => {
    expect(typeProbleme({ error: { type: 'https://vinasign.ca/problemes/lien-consomme' } })).toBe('lien-consomme');
    expect(typeProbleme({ error: {} })).toBe('inconnu');
    expect(typeProbleme(null)).toBe('inconnu');
  });
});
