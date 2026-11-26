import { TestBed } from '@angular/core/testing';
import { HttpTestingController } from '@angular/common/http/testing';
import { Router, provideRouter } from '@angular/router';
import { fournisseursDeTest } from '../../../../commun/src/test-outils';
import { CLE_IDEMPOTENCE, NouvelleEnveloppe } from './nouvelle-enveloppe';

const API = 'https://api.essai/api/v1';

describe('NouvelleEnveloppe (écran)', () => {
  let http: HttpTestingController;
  let el: HTMLElement;
  let detecter: () => void;
  let navigation: jasmine.Spy;

  function referentiel(type: string, valeurs: { code: string; attributs?: Record<string, string> }[]): void {
    http.match((r) => r.url === `${API}/referentiels/${type}`).forEach((r) => r.flush({ code: type, libelle: type, langue: 'fr',
      valeurs: valeurs.map((v, i) => ({ code: v.code, libelle: v.code, ordre: i, attributs: v.attributs ?? {} })) }));
  }

  function referentiels(): void {
    referentiel('mode-enveloppe', [{ code: 'empreinte' }, { code: 'document', attributs: { deposeDocument: 'true' } }]);
    referentiel('langue', [{ code: 'fr' }, { code: 'en' }]);
    referentiel('type-signataire', [{ code: 'utilisateur-application' }, { code: 'invite', attributs: { exigeCourriel: 'true' } }]);
    detecter();
  }

  beforeEach(() => {
    spyOnProperty(navigator, 'language', 'get').and.returnValue('fr-CA');
    TestBed.configureTestingModule({ imports: [NouvelleEnveloppe], providers: [...fournisseursDeTest(), provideRouter([]), { provide: CLE_IDEMPOTENCE, useValue: () => 'cle-essai' }] });
    navigation = spyOn(TestBed.inject(Router), 'navigate').and.resolveTo(true);
    const f = TestBed.createComponent(NouvelleEnveloppe);
    http = TestBed.inject(HttpTestingController);
    detecter = () => f.detectChanges();
    detecter();
    http.expectOne('i18n/fr.json').flush({});
    el = f.nativeElement as HTMLElement;
    referentiels();
  });

  function saisir(selecteur: string, valeur: string): void {
    const champ = el.querySelector(selecteur) as HTMLInputElement;
    champ.value = valeur;
    champ.dispatchEvent(new Event('input'));
  }

  function remplir(): File {
    saisir('input[name=reference]', '  DOSSIER-42 ');
    saisir('input[name=echeance]', '2026-12-31');
    const pdf = new File(['%PDF-1.7'], 'contrat.pdf', { type: 'application/pdf' });
    const fichier = el.querySelector('input[type=file]') as HTMLInputElement;
    const transfert = new DataTransfer();
    transfert.items.add(pdf);
    fichier.files = transfert.files;
    fichier.dispatchEvent(new Event('change'));
    saisir('input[name=nom0]', 'Zoé Gagnon');
    saisir('input[name=courriel0]', 'zoe@exemple.ca');
    (el.querySelectorAll('fieldset')[2].querySelector(':scope > button') as HTMLButtonElement).click();
    detecter();
    referentiels();
    saisir('input[name=nom1]', 'Léo');
    saisir('input[name=courriel1]', 'leo@exemple.ca');
    detecter();
    return pdf;
  }

  function soumettre(suite = 'envoyer'): void {
    (el.querySelector(`button[name=${suite}]`) as HTMLButtonElement).click();
    detecter();
  }

  it('n\'offre que les modes à document déposé et les signataires à courriel', () => {
    const listes = el.querySelectorAll('select');
    expect(Array.from(listes[0].options).map((o) => o.value)).toEqual(['document']);
    expect(Array.from(listes[2].options).map((o) => o.value)).toEqual(['invite']);
  });

  it('crée, dépose le PDF, définit les signataires dans l\'ordre, envoie, puis revient à la liste', () => {
    const pdf = remplir();
    soumettre();
    const creation = http.expectOne(`${API}/envelopes`);
    expect(creation.request.headers.get('Idempotency-Key')).toBe('cle-essai');
    expect(creation.request.body.externalReference).toBe('DOSSIER-42');
    expect(creation.request.body.mode).toBe('document');
    expect(creation.request.body.expiresAt).toBe(new Date('2026-12-31T23:59:59').toISOString());
    expect(el.querySelector('[aria-live]')?.textContent).toContain('envoi.etape.creation');
    el.querySelector('form')!.dispatchEvent(new Event('submit'));
    http.expectNone(`${API}/envelopes`);
    creation.flush({ id: 'e1', referenceExterne: 'DOSSIER-42', statut: 'brouillon' });
    http.expectOne(`${API}/envelopes/e1/documents`).flush({ id: 'd1', url: `${API}/depots/jeton`, expireLe: 'x' });
    const depot = http.expectOne(`${API}/depots/jeton`);
    expect(depot.request.body).toBe(pdf);
    depot.flush(null);
    http.expectOne(`${API}/envelopes/e1/documents/d1/confirmation`).flush({});
    const signataires = http.expectOne(`${API}/envelopes/e1/signers`);
    expect(signataires.request.body).toEqual([
      { nom: 'Zoé Gagnon', courriel: 'zoe@exemple.ca', langue: 'fr', type: 'invite', ordre: 1 },
      { nom: 'Léo', courriel: 'leo@exemple.ca', langue: 'fr', type: 'invite', ordre: 2 }]);
    signataires.flush({});
    http.expectOne(`${API}/envelopes/e1/send`).flush({});
    expect(navigation).toHaveBeenCalledWith(['/enveloppes'], { queryParams: { envoyee: 'e1' } });
  });

  it('en parallèle, tous signent au même rang ; un signataire se retire', () => {
    remplir();
    (el.querySelector('input[type=checkbox]') as HTMLInputElement).click();
    (el.querySelector('.signataire button') as HTMLButtonElement).click();
    detecter();
    expect(el.querySelectorAll('.signataire').length).toBe(1);
    soumettre();
    http.expectOne(`${API}/envelopes`).flush({ id: 'e2' });
    http.expectOne(`${API}/envelopes/e2/documents`).flush({ id: 'd2', url: `${API}/depots/j2`, expireLe: 'x' });
    http.expectOne(`${API}/depots/j2`).flush(null);
    http.expectOne(`${API}/envelopes/e2/documents/d2/confirmation`).flush({});
    expect(http.expectOne(`${API}/envelopes/e2/signers`).request.body).toEqual([{ nom: 'Léo', courriel: 'leo@exemple.ca', langue: 'fr', type: 'invite', ordre: 1 }]);
  });

  it('dit quelle étape a échoué et rend la main, saisie conservée', () => {
    remplir();
    soumettre();
    http.expectOne(`${API}/envelopes`).flush({ id: 'e3' });
    http.expectOne(`${API}/envelopes/e3/documents`).flush({ id: 'd3', url: `${API}/depots/j3`, expireLe: 'x' });
    http.expectOne(`${API}/depots/j3`).flush({}, { status: 422, statusText: 'Refusé' });
    detecter();
    expect(el.querySelector('[role=alert]')?.textContent).toContain('envoi.echec.depot');
    expect((el.querySelector('button[name=envoyer]') as HTMLButtonElement).disabled).toBeFalse();
    expect((el.querySelector('input[name=reference]') as HTMLInputElement).value).toBe('  DOSSIER-42 ');
    expect(navigation).not.toHaveBeenCalled();
    // Reprise : le brouillon existe, sa référence est figée ; seul le dépôt recommence, puis l'écran de placement s'ouvre.
    expect((el.querySelector('input[name=reference]') as HTMLInputElement).readOnly).toBeTrue();
    soumettre('placer');
    http.expectNone(`${API}/envelopes`);
    http.expectOne(`${API}/envelopes/e3/documents`).flush({ id: 'd4', url: `${API}/depots/j4`, expireLe: 'x' });
    http.expectOne(`${API}/depots/j4`).flush(null);
    http.expectOne(`${API}/envelopes/e3/documents/d4/confirmation`).flush({});
    http.expectOne(`${API}/envelopes/e3/signers`).flush({}, { status: 400, statusText: 'Refusé' });
    detecter();
    expect(el.querySelector('[role=alert]')?.textContent).toContain('envoi.echec.signataires');
    soumettre('placer');
    http.expectNone(`${API}/envelopes/e3/documents`);
    http.expectOne(`${API}/envelopes/e3/signers`).flush({});
    http.expectNone(`${API}/envelopes/e3/send`);
    expect(navigation).toHaveBeenCalledWith(['/enveloppes', 'e3']);
  });

  it('un fichier sans type connu du navigateur part en binaire : le serveur lit le vrai type', () => {
    remplir();
    const fichier = el.querySelector('input[type=file]') as HTMLInputElement;
    const t = new DataTransfer();
    t.items.add(new File(['PK'], 'contrat.docx'));
    fichier.files = t.files;
    fichier.dispatchEvent(new Event('change'));
    soumettre('placer');
    http.expectOne(`${API}/envelopes`).flush({ id: 'e5' });
    http.expectOne(`${API}/envelopes/e5/documents`).flush({ id: 'd5', url: `${API}/depots/j5`, expireLe: 'x' });
    expect(http.expectOne(`${API}/depots/j5`).request.headers.get('Content-Type')).toBe('application/octet-stream');
  });

  it('sans document choisi, rien ne part', () => {
    const fichier = el.querySelector('input[type=file]') as HTMLInputElement;
    fichier.files = new DataTransfer().files;
    fichier.dispatchEvent(new Event('change'));
    soumettre();
    http.expectNone(`${API}/envelopes`);
  });

  it('la clé d\'idempotence par défaut est un UUID aléatoire', () => {
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({});
    expect(TestBed.inject(CLE_IDEMPOTENCE)()).toMatch(/^[0-9a-f-]{36}$/);
  });
});
