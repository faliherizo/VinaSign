import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpTestingController } from '@angular/common/http/testing';
import { Referentiels } from './referentiels';
import { fournisseursDeTest } from '../../../../commun/src/test-outils';

describe('Referentiels (écran)', () => {
  let fixture: ComponentFixture<Referentiels>;
  let http: HttpTestingController;

  beforeEach(() => {
    spyOnProperty(navigator, 'language', 'get').and.returnValue('fr-CA');
    TestBed.configureTestingModule({ imports: [Referentiels], providers: fournisseursDeTest() });
    fixture = TestBed.createComponent(Referentiels);
    http = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
    http.expectOne('i18n/fr.json').flush({});
  });

  afterEach(() => http.verify());

  it('découvre chaque référentiel par l\'API et en affiche les valeurs', () => {
    http.expectOne((r) => r.url.endsWith('/api/v1/referentiels')).flush([{ code: 'x', libelle: 'X', nombreValeurs: 1 }]);
    http.expectOne((r) => r.url.endsWith('/api/v1/referentiels/x'))
      .flush({ code: 'x', libelle: 'Type X', langue: 'fr', valeurs: [{ code: 'v', libelle: 'Valeur V', ordre: 1, attributs: {} }] });
    fixture.detectChanges();
    const el = fixture.nativeElement as HTMLElement;
    expect(el.querySelector('section h2')?.textContent).toBe('Type X');
    expect(el.querySelector('section')?.getAttribute('aria-labelledby')).toBe('ref-x');
    expect(el.querySelector('tbody td:last-child')?.textContent).toBe('Valeur V');
  });

  it('n\'affiche rien sans référentiel', () => {
    http.expectOne((r) => r.url.endsWith('/api/v1/referentiels')).flush([]);
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('section')).toBeNull();
    expect(fixture.nativeElement.querySelector('[role="alert"]')).toBeNull();
  });

  it('annonce une erreur de chargement', () => {
    http.expectOne((r) => r.url.endsWith('/api/v1/referentiels')).flush('', { status: 503, statusText: 'indisponible' });
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('[role="alert"]')).not.toBeNull();
  });
});
