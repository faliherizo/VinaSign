import { TestBed } from '@angular/core/testing';
import { HttpTestingController } from '@angular/common/http/testing';
import { fournisseursDeTest } from '../../../../commun/src/test-outils';
import { Facturation, OUVRIR_PAGE, VueFacturation } from './facturation';

const API = 'https://api.essai/api/v1';

describe('Facturation (écran)', () => {
  let http: HttpTestingController;
  let ouvertes: string[];

  function ecran(situation: VueFacturation | null): { el: HTMLElement; detecter: () => void } {
    spyOnProperty(navigator, 'language', 'get').and.returnValue('fr-CA');
    ouvertes = [];
    TestBed.configureTestingModule({ imports: [Facturation], providers: [...fournisseursDeTest(), { provide: OUVRIR_PAGE, useValue: (u: string) => ouvertes.push(u) }] });
    const f = TestBed.createComponent(Facturation);
    http = TestBed.inject(HttpTestingController);
    f.detectChanges();
    http.expectOne('i18n/fr.json').flush({ 'facturation.erreur': 'Facturation indisponible', 'facturation.erreurPrestataire': 'Prestataire muet' });
    const requete = http.expectOne(`${API}/billing`);
    if (situation === null) {
      requete.flush({}, { status: 403, statusText: 'Forbidden' });
    } else {
      requete.flush(situation);
      http.expectOne((r) => r.url === `${API}/referentiels/plan` && r.params.get('langue') === 'fr').flush({ code: 'plan', libelle: 'Plan', langue: 'fr',
        valeurs: [{ code: 'essentiel', libelle: 'Essentiel', ordre: 1, attributs: {} }, { code: 'professionnel', libelle: 'Professionnel', ordre: 2, attributs: {} }] });
      http.expectOne(`${API}/referentiels/etat-abonnement?langue=fr`).flush({ code: 'etat-abonnement', libelle: 'État', langue: 'fr',
        valeurs: [{ code: 'actif', libelle: 'Actif', ordre: 1, attributs: {} }] });
    }
    f.detectChanges();
    return { el: f.nativeElement as HTMLElement, detecter: () => f.detectChanges() };
  }

  function listeDesPlans(): void {
    http.expectOne((r) => r.url === `${API}/referentiels/plan`).flush({ code: 'plan', libelle: 'Plan', langue: 'fr',
      valeurs: [{ code: 'essentiel', libelle: 'Essentiel', ordre: 1, attributs: {} }, { code: 'professionnel', libelle: 'Professionnel', ordre: 2, attributs: {} }] });
  }

  it('affiche le plan et l\'état par leurs libellés du référentiel, et les factures dans la langue choisie', () => {
    const { el, detecter } = ecran({ plan: 'professionnel', etat: 'actif', factures: [
      { numero: 'F-001', montant: 4900, devise: 'cad', url: 'https://factures.x/1', pdf: 'https://factures.x/1.pdf', debut: '2026-10-01T00:00:00Z', fin: '2026-11-01T00:00:00Z', payeeLe: '2026-10-01T00:00:00Z' },
      { numero: 'F-002', montant: 4900, devise: 'cad', url: null, pdf: null, debut: null, fin: null, payeeLe: null },
      { numero: 'F-003', montant: 100, devise: 'cad', url: null, pdf: null, debut: null, fin: null, payeeLe: null }] });
    listeDesPlans();
    detecter();
    expect(el.querySelector('.abonnement')?.textContent).toContain('Professionnel');
    expect(el.querySelector('.abonnement')?.textContent).toContain('Actif');
    const lignes = el.querySelectorAll('tbody tr');
    expect(lignes.length).toBe(3);
    expect(lignes[0].textContent).toContain('49,00');
    expect(lignes[0].querySelectorAll('a').length).toBe(2);
    expect(lignes[0].querySelector('a')?.getAttribute('rel')).toBe('noopener noreferrer');
    expect(lignes[1].querySelectorAll('a').length).toBe(0);
    expect(el.querySelector('.defilement')?.getAttribute('tabindex')).toBe('0');
  });

  it('un code inconnu du référentiel reste lisible par son code', () => {
    const { el, detecter } = ecran({ plan: 'sur-mesure', etat: 'impaye', factures: [] });
    listeDesPlans();
    detecter();
    expect(el.querySelector('.abonnement')?.textContent).toContain('sur-mesure');
    expect(el.querySelector('.abonnement')?.textContent).toContain('impaye');
  });

  it('paie le plan choisi chez le prestataire, dans la langue de l\'interface', () => {
    const { el, detecter } = ecran({ plan: null, etat: null, factures: [] });
    listeDesPlans();
    detecter();
    expect(el.querySelector('.abonnement')).toBeNull();
    expect(el.querySelectorAll('section button').length).toBe(1);
    (el.querySelector('form button') as HTMLButtonElement).click();
    const r = http.expectOne(`${API}/billing/checkout`);
    expect(r.request.body).toEqual({ plan: 'essentiel', langue: 'fr' });
    r.flush({ url: 'https://checkout.prestataire.x/s/1' });
    expect(ouvertes).toEqual(['https://checkout.prestataire.x/s/1']);
  });

  it('gère l\'abonnement sur la page du prestataire ; une panne le dit et rend la main', () => {
    const { el, detecter } = ecran({ plan: 'essentiel', etat: 'actif', factures: [] });
    listeDesPlans();
    detecter();
    (el.querySelector('.abonnement + button') as HTMLButtonElement).click();
    detecter();
    expect((el.querySelector('.abonnement + button') as HTMLButtonElement).disabled).toBeTrue();
    http.expectOne(`${API}/billing/portal`).flush({}, { status: 503, statusText: 'Unavailable' });
    detecter();
    expect(el.querySelector('[role=alert]')?.textContent).toBe('Prestataire muet');
    expect((el.querySelector('.abonnement + button') as HTMLButtonElement).disabled).toBeFalse();
    expect(ouvertes).toEqual([]);
  });

  it('une facturation inaccessible le dit', () => {
    const { el } = ecran(null);
    expect(el.querySelector('[role=alert]')?.textContent).toBe('Facturation indisponible');
    expect(el.querySelector('section')).toBeNull();
  });

  it('ouvre la page du prestataire dans l\'onglet courant', () => {
    TestBed.configureTestingModule({});
    TestBed.inject(OUVRIR_PAGE)('#facturation-essai');
    expect(window.location.hash).toBe('#facturation-essai');
    history.replaceState(null, '', window.location.pathname + window.location.search);
  });
});
