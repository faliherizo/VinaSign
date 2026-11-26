import { TestBed } from '@angular/core/testing';
import { HttpTestingController } from '@angular/common/http/testing';
import { Accueil } from './accueil';
import { fournisseursDeTest } from '../../../../commun/src/test-outils';

describe('Accueil', () => {
  it('affiche un titre de niveau 1 traduit', () => {
    spyOnProperty(navigator, 'language', 'get').and.returnValue('fr-CA');
    TestBed.configureTestingModule({ imports: [Accueil], providers: fournisseursDeTest() });
    const fixture = TestBed.createComponent(Accueil);
    fixture.detectChanges();
    TestBed.inject(HttpTestingController).expectOne('i18n/fr.json').flush({ 'accueil.titre': 'Bienvenue' });
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('h1').textContent).toBe('Bienvenue');
  });
});
