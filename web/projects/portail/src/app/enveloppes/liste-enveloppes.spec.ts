import { TestBed } from '@angular/core/testing';
import { HttpTestingController, TestRequest } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { fournisseursDeTest } from '../../../../commun/src/test-outils';
import { CLIENT_OIDC } from '../connexion/client-oidc';
import { SessionPortail } from '../connexion/session-portail';
import { ListeEnveloppes } from './liste-enveloppes';
import { ResumeEnveloppe } from './enveloppes.service';
import { FauxClientOidc, utilisateur } from '../../faux-client-oidc';

const API = 'https://api.essai/api/v1';

function resume(id: string, statut = 'envoyee'): ResumeEnveloppe {
  return { id, referenceExterne: 'REF-' + id, mode: 'document', statut, expireLe: '2026-12-31T00:00:00Z', creeLe: '2026-11-01T00:00:00Z', signataires: 2, signes: 1 };
}

describe('ListeEnveloppes (écran)', () => {
  let http: HttpTestingController;

  function ecran(roles: string[]): { el: HTMLElement; detecter: () => void } {
    spyOnProperty(navigator, 'language', 'get').and.returnValue('fr-CA');
    const client = new FauxClientOidc();
    TestBed.configureTestingModule({ imports: [ListeEnveloppes], providers: [...fournisseursDeTest(), provideRouter([]), { provide: CLIENT_OIDC, useValue: client }] });
    TestBed.inject(SessionPortail);
    client.chargerUtilisateur(utilisateur(roles));
    const f = TestBed.createComponent(ListeEnveloppes);
    http = TestBed.inject(HttpTestingController);
    f.detectChanges();
    http.expectOne('i18n/fr.json').flush({ 'enveloppes.tous': 'Tous', 'enveloppes.erreur': 'Indisponible' });
    return { el: f.nativeElement as HTMLElement, detecter: () => f.detectChanges() };
  }

  function statuts(): TestRequest[] {
    return http.match((r) => r.url === `${API}/referentiels/statut-enveloppe`);
  }

  function repondreStatuts(): void {
    statuts().forEach((r) => r.flush({ code: 'statut-enveloppe', libelle: 'Statut', langue: 'fr', valeurs: [
      { code: 'brouillon', libelle: 'Brouillon', ordre: 1, attributs: {} }, { code: 'envoyee', libelle: 'Envoyée', ordre: 2, attributs: {} }] }));
  }

  it('affiche les enveloppes avec le libellé de leur statut, puis la page suivante', () => {
    const { el, detecter } = ecran(['preparateur']);
    http.expectOne(`${API}/envelopes?limite=25`).flush({ elements: [resume('1'), resume('2', 'inconnu')], suivant: 'c1' });
    repondreStatuts();
    detecter();
    const cartes = el.querySelectorAll('.cartes li');
    expect(cartes.length).toBe(2);
    expect(cartes[0].textContent).toContain('Envoyée');
    expect(cartes[0].textContent).toContain('1 / 2');
    expect(cartes[1].textContent).toContain('inconnu');
    expect(el.querySelector('a[href="/enveloppes/nouvelle"]')).not.toBeNull();
    (el.querySelector('.cartes + button') as HTMLButtonElement).click();
    http.expectOne(`${API}/envelopes?limite=25&apres=c1`).flush({ elements: [resume('3')], suivant: null });
    detecter();
    expect(el.querySelectorAll('.cartes li').length).toBe(3);
    expect(el.querySelector('.cartes + button')).toBeNull();
  });

  it('filtre par statut, le premier choix étant « Tous »', () => {
    const { el, detecter } = ecran(['auditeur']);
    http.expectOne(`${API}/envelopes?limite=25`).flush({ elements: [], suivant: null });
    repondreStatuts();
    detecter();
    expect(el.querySelector('a[href="/enveloppes/nouvelle"]')).toBeNull();
    expect(el.textContent).toContain('enveloppes.aucune');
    const select = el.querySelector('select') as HTMLSelectElement;
    expect(select.options[0].textContent?.trim()).toBe('Tous');
    select.value = 'brouillon';
    select.dispatchEvent(new Event('change'));
    detecter();
    http.expectOne(`${API}/envelopes?limite=25&statut=brouillon`).flush({ elements: [resume('9', 'brouillon')], suivant: null });
    detecter();
    expect(el.querySelectorAll('.cartes li').length).toBe(1);
  });

  it('dit quand la liste ne se charge pas', () => {
    const { el, detecter } = ecran(['preparateur']);
    http.expectOne(`${API}/envelopes?limite=25`).flush({}, { status: 503, statusText: 'Indisponible' });
    statuts().forEach((r) => r.flush({}, { status: 503, statusText: 'Indisponible' }));
    detecter();
    expect(el.querySelector(':scope > p.erreur')?.textContent).toBe('Indisponible');
  });
});
