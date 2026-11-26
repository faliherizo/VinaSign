import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpTestingController, TestRequest } from '@angular/common/http/testing';
import { Etat, VueEtat } from './etat';
import { fournisseursDeTest } from '../../../../commun/src/test-outils';

function ref(code: string, valeurs: { code: string; libelle: string; attributs?: Record<string, string> }[]) {
  return { code, libelle: code, langue: 'fr', valeurs: valeurs.map((v, i) => ({ ordre: i + 1, attributs: {}, ...v })) };
}

const REFS: Record<string, object> = {
  'impact-incident': ref('impact-incident', [{ code: 'ok', libelle: 'Opérationnel' }, { code: 'panne', libelle: 'Panne majeure' }]),
  'composant-service': ref('composant-service', [{ code: 'api', libelle: 'API' }, { code: 'portail', libelle: 'Portail' }]),
  'phase-incident': ref('phase-incident', [{ code: 'enquete', libelle: 'Enquête' }, { code: 'resolu', libelle: 'Résolu' }]),
  'objectif-service': ref('objectif-service', [{ code: 'dispo', libelle: 'Disponibilité', attributs: { cible: '99.9', unite: '%' } }]),
};

describe('Etat (page d\'état, VS-124)', () => {
  let fixture: ComponentFixture<Etat>;
  let http: HttpTestingController;

  beforeEach(() => {
    spyOnProperty(navigator, 'language', 'get').and.returnValue('fr-CA');
    TestBed.configureTestingModule({ imports: [Etat], providers: fournisseursDeTest() });
    fixture = TestBed.createComponent(Etat);
    http = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
    http.expectOne('i18n/fr.json').flush({});
  });

  afterEach(() => http.verify());

  function repondre(etat: VueEtat): void {
    http.expectOne((r) => r.url.endsWith('/api/v1/availability')).flush(etat);
    Object.entries(REFS).forEach(([code, corps]) => http.expectOne((r: TestRequest['request']) => r.url.endsWith('/api/v1/referentiels/' + code)).flush(corps));
    fixture.detectChanges();
  }

  const el = () => fixture.nativeElement as HTMLElement;

  it('affiche l\'impact global, chaque composant, les objectifs et un incident en cours', () => {
    repondre({
      impact: 'panne', composants: { api: 'panne', portail: 'ok' }, le: '2026-11-22T12:00:00Z',
      incidents: [{ id: 'i1', titre: { fr: 'Lenteurs', en: 'Slowness' }, impact: 'panne', composants: ['api'], debut: '2026-11-22T11:00:00Z', fin: null,
        misesAJour: [{ phase: 'enquete', message: { en: 'Looking' }, le: '2026-11-22T11:00:00Z' }] }],
    });
    expect(el().querySelector('.global')!.textContent).toContain('Panne majeure');
    expect(el().querySelector('.global')!.classList).toContain('perturbe');
    const composants = [...el().querySelectorAll('.composants li')];
    expect(composants.map((c) => [...c.querySelectorAll('span')].map((x) => x.textContent).join(' '))).toEqual(['API Panne majeure', 'Portail Opérationnel']);
    expect(composants[0].classList).toContain('perturbe');
    expect(el().querySelector('article.incident h3')!.textContent).toBe('Lenteurs');
    expect(el().querySelector('article .communications li')!.textContent).toContain('Looking');
    expect(el().querySelector('tbody td:last-child')!.textContent!.trim()).toBe('99.9 %');
    expect(el().textContent).toContain('etat.aucunHistorique');
  });

  it('range un incident résolu dans l\'historique, et garde un code inconnu tel quel', () => {
    repondre({
      impact: 'ok', composants: { api: 'retire' }, le: '2026-11-22T12:00:00Z',
      incidents: [{ id: 'i2', titre: {}, impact: 'ok', composants: ['api'], debut: '2026-11-20T11:00:00Z', fin: '2026-11-20T12:00:00Z',
        misesAJour: [{ phase: 'resolu', message: { fr: 'Corrigé' }, le: '2026-11-20T12:00:00Z' }] }],
    });
    expect(el().querySelector('.global')!.classList).not.toContain('perturbe');
    expect(el().querySelector('.composants li')!.textContent).toContain('retire');
    expect(el().querySelector('article.incident')).toBeNull();
    expect(el().textContent).toContain('etat.aucun');
    expect(el().querySelector('details.incident .communications')!.textContent).toContain('Résolu : Corrigé');
  });

  it('avant toute réponse, rend des valeurs neutres sans planter', () => {
    const page = fixture.componentInstance as unknown as {
      libelle(r: string, c: string): string; gravite(c: string): number; objectifs(): unknown[]; composants(): string[]; date(i: string | null, s: string): string;
      enCours(): unknown[]; passes(): unknown[];
    };
    expect(page.libelle('impacts', 'x')).toBe('x');
    expect(page.gravite('x')).toBe(0);
    expect(page.objectifs()).toEqual([]);
    expect(page.composants()).toEqual([]);
    expect(page.enCours()).toEqual([]);
    expect(page.passes()).toEqual([]);
    expect(page.date(null, 'short')).toBe('');
    http.match(() => true);
  });

  it('annonce une erreur de chargement', () => {
    http.expectOne((r) => r.url.endsWith('/api/v1/availability')).flush('', { status: 503, statusText: 'indisponible' });
    http.match((r) => r.url.includes('/api/v1/referentiels/'));
    fixture.detectChanges();
    expect(el().querySelector('[role="alert"]')).not.toBeNull();
    expect(el().querySelector('.global')).toBeNull();
  });
});
