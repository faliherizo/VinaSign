import { TestBed } from '@angular/core/testing';
import { Location } from '@angular/common';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting, TestRequest } from '@angular/common/http/testing';
import { CONFIGURATION } from '@vinasign/commun';
import { CONFIGURATION_TEST } from '../../../../commun/src/test-outils';
import { intercepteurSession } from '../api/session-signataire';
import { VueSession } from '../api/modeles';
import { ChargeurPdf, DocumentPdf } from '@vinasign/commun';
import { champ, typesDeChamp } from '../champs/types-champ.spec';
import { Avis, AvisParent } from './avis-parent';
import { Parcours } from './parcours';

const API = 'https://api.essai/api/v1/signataire/';

class ChargeurFactice extends ChargeurPdf {
  ouvrir(): Promise<DocumentPdf> {
    return Promise.resolve({ nombrePages: 1, rendre: async () => 1.4, texte: async () => 'Contrat', fermer: async () => undefined });
  }
}

function vue(changes: Partial<VueSession> = {}): VueSession {
  return {
    jetonSession: 'session-1', expireLe: '2026-11-22T20:00:00Z', enveloppe: 'e', mode: 'sequentiel', statutEnveloppe: 'en-cours', nom: 'Marie',
    langue: 'fr', peutAgir: true, aConsenti: false, versionConsentement: 'v1', texteConsentement: 'Je consens.', actions: [], originesIntegration: [], niveauExige: null, niveauAtteint: null, ...changes,
  };
}

function creer(chemin = '/s/abc123') {
  const location = jasmine.createSpyObj<Location>('Location', ['path', 'replaceState']);
  location.path.and.returnValue(chemin);
  const avis: Avis[] = [];
  const autorisees: string[][] = [];
  TestBed.configureTestingModule({
    imports: [Parcours],
    providers: [
      provideHttpClient(withInterceptors([intercepteurSession])), provideHttpClientTesting(), { provide: CONFIGURATION, useValue: CONFIGURATION_TEST },
      { provide: Location, useValue: location }, { provide: ChargeurPdf, useClass: ChargeurFactice },
      { provide: AvisParent, useValue: { signaler: (a: Avis) => avis.push(a), autoriser: (o: string[]) => autorisees.push(o) } },
    ],
  });
  const http = TestBed.inject(HttpTestingController);
  const fixture = TestBed.createComponent(Parcours);
  fixture.componentRef.setInput('jeton', 'abc123');
  fixture.detectChanges();
  const el = fixture.nativeElement as HTMLElement;
  const rendre = () => fixture.detectChanges();
  const bouton = (cle: string) => [...el.querySelectorAll('button')].find((b) => b.textContent!.trim() === cle) as HTMLButtonElement | undefined;
  return { fixture, http, el, location, avis, autorisees, rendre, bouton };
}

function traductions(http: HttpTestingController, textes: Record<string, string> = {}): void {
  http.match((r) => r.url.startsWith('i18n/')).forEach((r) => r.flush(textes));
}

function ouvrir(http: HttpTestingController, s: VueSession): TestRequest {
  const r = http.expectOne(API + 'ouverture');
  r.flush(s);
  return r;
}

async function jusquaSignature(c: ReturnType<typeof creer>, champs = [champ('t', 'texte'), champ('s', 'signature'), champ('f', 'texte', false)]) {
  ouvrir(c.http, vue({ aConsenti: true }));
  c.http.expectOne(API + 'document').flush(new ArrayBuffer(4));
  c.http.expectOne(API + 'champs').flush(champs);
  c.rendre();
  typesDeChamp(c.http);
  c.rendre();
  await c.fixture.whenStable();
  c.rendre();
}

describe('Parcours du signataire', () => {
  // Langue du navigateur fixée : sur un poste en anglais (exécuteur de la CI), la langue de la session (fr) succède à
  // celle du navigateur et chaque référentiel serait demandé deux fois.
  beforeEach(() => spyOnProperty(navigator, 'language', 'get').and.returnValue('fr-CA'));

  it('retire le jeton de la barre d\'adresse et l\'échange dans le corps de la requête', () => {
    const c = creer('/integre/abc123');
    expect(c.location.replaceState).toHaveBeenCalledWith('/integre');
    expect(c.http.expectOne(API + 'ouverture').request.body).toEqual({ jeton: 'abc123' });
    expect(c.el.querySelector('[role="status"]')!.textContent).toContain('parcours.ouverture');
  });

  it('remplace par la racine un chemin réduit au jeton', () => {
    const c = creer('abc123');
    expect(c.location.replaceState).toHaveBeenCalledWith('/');
  });

  it('applique la marque de l\'organisation : couleur des actions, logo et nom', () => {
    const c = creer();
    ouvrir(c.http, vue({ marque: { nomAffiche: 'Clinique Nord', couleurPrimaire: '#0b5394', logo: 'data:image/png;base64,iVBORw0KGgo=' } }));
    c.rendre();
    expect(c.el.style.getPropertyValue('--marque')).toBe('#0b5394');
    expect(c.el.querySelector('.marque span')!.textContent).toBe('Clinique Nord');
    expect(c.el.querySelector<HTMLImageElement>('.marque img')!.src).toBe('data:image/png;base64,iVBORw0KGgo=');
    TestBed.resetTestingModule();
    const sansLogo = creer();
    ouvrir(sansLogo.http, vue({ marque: { nomAffiche: 'Nord', couleurPrimaire: '#000000', logo: null } }));
    sansLogo.rendre();
    expect(sansLogo.el.querySelector('.marque img')).toBeNull();
  });

  it('sans marque, garde la couleur par défaut et n\'affiche aucun en-tête', () => {
    const c = creer();
    ouvrir(c.http, vue());
    c.rendre();
    expect(c.el.style.getPropertyValue('--marque')).toBe('');
    expect(c.el.querySelector('.marque')).toBeNull();
  });

  it('présente l\'interface dans la langue de l\'invitation', () => {
    const c = creer();
    ouvrir(c.http, vue({ langue: 'en', peutAgir: false, originesIntegration: ['https://rh.exemple.ca'] }));
    c.rendre();
    expect(c.autorisees).toEqual([['https://rh.exemple.ca']]);
    expect(document.documentElement.lang).toBe('en');
    expect(c.http.match((r) => r.url === 'i18n/en.json').length).toBeGreaterThan(0);
  });

  it('fait consentir, puis présente le document, les champs et la signature, et signe', async () => {
    const c = creer();
    ouvrir(c.http, vue());
    c.rendre();
    expect(c.avis).toEqual(['ready']);
    expect(document.documentElement.lang).toBe('fr');
    expect(c.el.textContent).toContain('Je consens.');
    expect(c.el.textContent).toContain('Marie');
    const continuer = c.bouton('consentement.continuer')!;
    expect(continuer.disabled).toBeTrue();
    c.el.querySelector<HTMLInputElement>('#accepte')!.click();
    c.rendre();
    continuer.click();
    const consentement = c.http.expectOne(API + 'consentement');
    expect(consentement.request.body).toEqual({ version: 'v1' });
    expect(consentement.request.headers.get('X-Session-Signataire')).toBe('session-1');
    consentement.flush({});
    c.http.expectOne(API + 'document').flush(new ArrayBuffer(4));
    c.http.expectOne(API + 'champs').flush([champ('t', 'texte'), champ('s', 'signature'), champ('f', 'texte', false)]);
    c.rendre();
    typesDeChamp(c.http);
    c.rendre();
    await c.fixture.whenStable();
    c.rendre();
    expect(c.el.querySelector('vs-visionneuse-pdf figure')).not.toBeNull();

    c.bouton('signature.signer')!.click();
    c.rendre();
    expect(c.el.querySelector('.message')!.textContent).toContain('signature.champsManquants');
    expect(c.el.querySelector('#champ-t')!.getAttribute('aria-invalid')).toBe('true');
    c.http.expectNone(API + 'signature');

    const t = c.el.querySelector<HTMLInputElement>('#champ-t')!;
    t.value = 'Lévis';
    t.dispatchEvent(new Event('input'));
    const f = c.el.querySelector<HTMLInputElement>('#champ-f')!;
    f.value = '  ';
    f.dispatchEvent(new Event('input'));
    c.bouton('signature.signer')!.click();
    c.rendre();
    expect(c.el.querySelector('.message')!.textContent).toContain('signature.absente');

    [...c.el.querySelectorAll<HTMLButtonElement>('[role="radio"]')][1].click();
    c.rendre();
    const nom = c.el.querySelector<HTMLInputElement>('#nom-tape')!;
    nom.value = 'Marie Tremblay';
    nom.dispatchEvent(new Event('input'));
    c.bouton('signature.signer')!.click();
    const signature = c.http.expectOne(API + 'signature');
    expect(signature.request.body).toEqual({ nomSaisi: 'Marie Tremblay', valeurs: { t: 'Lévis', s: 'saisie' } });
    signature.flush(null, { status: 204, statusText: 'No Content' });
    c.rendre();
    expect(c.el.querySelector('.succes')!.textContent).toContain('fin.signe');
    expect(c.avis).toEqual(['ready', 'signed']);
  });

  it('remplit les zones de signature par « dessin » quand la signature est dessinée', async () => {
    const c = creer();
    await jusquaSignature(c, [champ('s', 'signature')]);
    const toile = c.el.querySelector<HTMLCanvasElement>('vs-pad-signature canvas')!;
    toile.dispatchEvent(new PointerEvent('pointerdown', { clientX: 1, clientY: 1, pointerId: 1 }));
    toile.dispatchEvent(new PointerEvent('pointermove', { clientX: 40, clientY: 30, pointerId: 1 }));
    toile.dispatchEvent(new PointerEvent('pointerup', { clientX: 40, clientY: 30, pointerId: 1 }));
    c.bouton('signature.signer')!.click();
    const corps = c.http.expectOne(API + 'signature').request.body;
    expect(corps.valeurs).toEqual({ s: 'dessin' });
    expect(corps.image).toMatch(/^iVBOR/);
  });

  it('refuse avec un motif du référentiel, après avoir pu revenir au document', async () => {
    const c = creer();
    await jusquaSignature(c);
    c.bouton('refus.ouvrir')!.click();
    c.rendre();
    c.http.expectOne((r) => r.url.endsWith('/referentiels/motif-refus')).flush({
      code: 'motif-refus', libelle: 'Motif', langue: 'fr', valeurs: [{ code: 'contenu-incorrect', libelle: 'Contenu', ordre: 1, attributs: {} }],
    });
    c.rendre();
    c.bouton('refus.annuler')!.click();
    c.rendre();
    expect(c.bouton('signature.signer')).toBeDefined();
    c.bouton('refus.ouvrir')!.click();
    c.rendre();
    c.http.match((r) => r.url.endsWith('/referentiels/motif-refus')).forEach((r) =>
      r.flush({ code: 'motif-refus', libelle: 'Motif', langue: 'fr', valeurs: [{ code: 'contenu-incorrect', libelle: 'Contenu', ordre: 1, attributs: {} }] }));
    c.rendre();
    c.bouton('refus.confirmer')!.click();
    const refus = c.http.expectOne(API + 'refus');
    expect(refus.request.body).toEqual({ motif: 'contenu-incorrect' });
    refus.flush(null, { status: 204, statusText: 'No Content' });
    c.rendre();
    expect(c.el.textContent).toContain('fin.refuse');
    expect(c.avis.at(-1)).toBe('declined');
  });

  it('refuse sans motif si la liste est indisponible', async () => {
    const c = creer();
    await jusquaSignature(c);
    c.bouton('refus.ouvrir')!.click();
    c.rendre();
    c.http.expectOne((r) => r.url.endsWith('/referentiels/motif-refus')).flush('', { status: 503, statusText: 'x' });
    c.rendre();
    c.bouton('refus.confirmer')!.click();
    expect(c.http.expectOne(API + 'refus').request.body).toEqual({ motif: null });
  });

  it('ne propose que les actions permises par la session intégrée', async () => {
    const lecture = creer();
    ouvrir(lecture.http, vue({ aConsenti: true, actions: ['consulter'] }));
    lecture.http.expectOne(API + 'document').flush(new ArrayBuffer(4));
    lecture.http.expectOne(API + 'champs').flush([]);
    lecture.rendre();
    expect(lecture.bouton('signature.signer')).toBeUndefined();
    expect(lecture.bouton('refus.ouvrir')).toBeUndefined();

    TestBed.resetTestingModule();
    const refus = creer();
    ouvrir(refus.http, vue({ aConsenti: true, actions: ['consulter', 'refuser'] }));
    refus.http.expectOne(API + 'document').flush(new ArrayBuffer(4));
    refus.http.expectOne(API + 'champs').flush([]);
    refus.rendre();
    expect(refus.bouton('signature.signer')).toBeUndefined();
    refus.bouton('refus.ouvrir')!.click();
    refus.rendre();
    expect(refus.el.textContent).toContain('refus.explication');
  });

  it('exige la vérification d\'identité avant le document quand un niveau est requis, puis la passe', async () => {
    const c = creer();
    ouvrir(c.http, vue({ aConsenti: true, niveauExige: 'renforce' }));
    c.rendre();
    expect(c.el.querySelector('vs-etape-authentification')).not.toBeNull();
    c.http.expectNone(API + 'document');
    const etape = c.fixture.debugElement.query((d) => d.name === 'vs-etape-authentification');
    etape.triggerEventHandler('authentifie');
    c.http.expectOne(API + 'document').flush(new ArrayBuffer(4));
    c.http.expectOne(API + 'champs').flush([]);
    c.rendre();
    expect(c.el.querySelector('vs-etape-authentification')).toBeNull();
    expect(c.el.querySelector('vs-visionneuse-pdf')).not.toBeNull();
  });

  it('ne redemande pas l\'identité déjà attestée par l\'application', () => {
    const c = creer();
    ouvrir(c.http, vue({ aConsenti: true, niveauExige: 'renforce', niveauAtteint: 'renforce' }));
    c.http.expectOne(API + 'document').flush(new ArrayBuffer(4));
    c.http.expectOne(API + 'champs').flush([]);
  });

  it('avise l\'application quand la page se ferme', () => {
    const c = creer();
    window.dispatchEvent(new Event('pagehide'));
    expect(c.avis).toEqual(['closed']);
  });

  it('fait patienter le signataire qui ne peut pas encore agir', () => {
    const c = creer();
    ouvrir(c.http, vue({ peutAgir: false }));
    c.rendre();
    expect(c.el.querySelector('[role="status"]')!.textContent).toContain('parcours.attente');
  });

  it('explique un lien refusé dans la langue courante, et retombe sur un message général', () => {
    const c = creer();
    traductions(c.http, { 'erreur.lien-consomme': 'Ce lien a déjà servi.', 'erreur.inconnu': 'Erreur.' });
    c.http.expectOne(API + 'ouverture').flush({ type: 'https://vinasign.ca/problemes/lien-consomme' }, { status: 410, statusText: 'Gone' });
    c.rendre();
    expect(c.el.querySelector('[role="alert"]')!.textContent).toContain('Ce lien a déjà servi.');
    expect(c.avis).toEqual(['error']);

    TestBed.resetTestingModule();
    const d = creer();
    traductions(d.http, { 'erreur.inconnu': 'Erreur.' });
    d.http.expectOne(API + 'ouverture').flush({ type: 'https://vinasign.ca/problemes/autre-chose' }, { status: 500, statusText: 'x' });
    d.rendre();
    expect(d.el.querySelector('[role="alert"]')!.textContent).toContain('Erreur.');
  });

  it('rend compte d\'un échec du consentement, du chargement, de la signature ou du refus', async () => {
    const c = creer();
    ouvrir(c.http, vue());
    c.rendre();
    c.el.querySelector<HTMLInputElement>('#accepte')!.click();
    c.rendre();
    c.bouton('consentement.continuer')!.click();
    c.http.expectOne(API + 'consentement').flush({}, { status: 409, statusText: 'x' });
    c.rendre();
    expect(c.el.querySelector('[role="alert"]')).not.toBeNull();

    TestBed.resetTestingModule();
    const d = creer();
    ouvrir(d.http, vue({ aConsenti: true }));
    d.http.expectOne(API + 'document').flush(new ArrayBuffer(0), { status: 500, statusText: 'x' });
    d.rendre();
    expect(d.el.querySelector('[role="alert"]')).not.toBeNull();

    TestBed.resetTestingModule();
    const e = creer();
    await jusquaSignature(e, []);
    [...e.el.querySelectorAll<HTMLButtonElement>('[role="radio"]')][1].click();
    e.rendre();
    const nom = e.el.querySelector<HTMLInputElement>('#nom-tape')!;
    nom.value = 'M';
    nom.dispatchEvent(new Event('input'));
    e.bouton('signature.signer')!.click();
    e.http.expectOne(API + 'signature').flush({}, { status: 409, statusText: 'x' });
    e.rendre();
    expect(e.el.querySelector('[role="alert"]')).not.toBeNull();

    TestBed.resetTestingModule();
    const f = creer();
    await jusquaSignature(f, []);
    f.bouton('refus.ouvrir')!.click();
    f.rendre();
    f.bouton('refus.confirmer')!.click();
    f.http.expectOne(API + 'refus').flush({}, { status: 409, statusText: 'x' });
    f.rendre();
    expect(f.el.querySelector('[role="alert"]')).not.toBeNull();
  });
});
