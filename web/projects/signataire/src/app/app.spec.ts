import { TestBed } from '@angular/core/testing';
import { HttpTestingController } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { App } from './app';
import { configurationApplication } from './app.config';
import { CONFIGURATION } from '@vinasign/commun';
import { CONFIGURATION_TEST, fournisseursDeTest } from '../../../commun/src/test-outils';

describe('App (signataire)', () => {
  it('affiche un titre traduit et le choix de langue par référentiel', () => {
    spyOnProperty(navigator, 'language', 'get').and.returnValue('en-CA');
    TestBed.configureTestingModule({ imports: [App], providers: [...fournisseursDeTest(), provideRouter([])] });
    const fixture = TestBed.createComponent(App);
    const http = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
    http.expectOne('i18n/en.json').flush({ titre: 'Sign a document' });
    http.expectOne((r) => r.url.endsWith('/api/v1/referentiels/langue')).flush({ code: 'langue', libelle: 'Language', langue: 'en', valeurs: [] });
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('h1').textContent).toBe('Sign a document');
    expect(fixture.nativeElement.querySelector('label').textContent).toBe('Language');
    const pied = fixture.nativeElement.querySelector('footer.pied') as HTMLElement;
    expect(pied.textContent).withContext('aide au même endroit à chaque étape (WCAG 3.2.6)').toContain('pied.aide');
    expect(pied.textContent).toContain('pied.lienPersonnel');
  });

  it('injecte la configuration lue à l\'exécution', () => {
    const fournisseurs = configurationApplication(CONFIGURATION_TEST).providers as { provide?: unknown; useValue?: unknown }[];
    expect(fournisseurs.find((f) => f.provide === CONFIGURATION)?.useValue).toBe(CONFIGURATION_TEST);
  });
});
