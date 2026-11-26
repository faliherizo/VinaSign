import { TestBed } from '@angular/core/testing';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { CONFIGURATION } from '@vinasign/commun';
import { CONFIGURATION_TEST } from '../../../../commun/src/test-outils';
import { intercepteurSession } from '../api/session-signataire';
import { ClesNavigateur } from './cles-navigateur';
import { EtapeAuthentification, SUIVI_IDENTITE } from './etape-authentification';

const API = 'https://api.essai/api/v1/signataire/';
const OPTIONS = { defi: 'AAAA', rpId: 'signer.vinasign.test', identifiantUtilisateur: 'AQID', nomAffiche: 'Gabrielle', clesPermises: ['BBBB'], delaiMs: 300000 };

function creer(disponible: boolean) {
  const cles = jasmine.createSpyObj<ClesNavigateur>('ClesNavigateur', ['disponible', 'creer', 'utiliser']);
  cles.disponible.and.returnValue(disponible);
  cles.utiliser.and.resolveTo({ credentialId: 'c', authenticatorData: 'a', clientDataJSON: 'j', signature: 's' });
  cles.creer.and.resolveTo({ attestationObject: 'o', clientDataJSON: 'j' });
  TestBed.configureTestingModule({
    imports: [EtapeAuthentification],
    providers: [provideHttpClient(withInterceptors([intercepteurSession])), provideHttpClientTesting(), { provide: CONFIGURATION, useValue: CONFIGURATION_TEST },
      { provide: ClesNavigateur, useValue: cles }],
  });
  const fixture = TestBed.createComponent(EtapeAuthentification);
  let authentifie = 0;
  fixture.componentInstance.authentifie.subscribe(() => authentifie++);
  fixture.detectChanges();
  const http = TestBed.inject(HttpTestingController);
  http.match((r) => r.url.startsWith('i18n/')).forEach((r) => r.flush({}));
  http.match((r) => r.url.endsWith('/referentiels/canal-code')).forEach((r) => r.flush({ code: 'canal-code', libelle: 'Canal', langue: 'fr',
    valeurs: [{ code: 'courriel', libelle: 'Courriel', ordre: 1, attributs: {} }] }));
  fixture.detectChanges();
  const el = fixture.nativeElement as HTMLElement;
  const bouton = (cle: string) => [...el.querySelectorAll('button')].find((b) => b.textContent!.trim() === cle) as HTMLButtonElement | undefined;
  const attendre = async () => {
    await fixture.whenStable();
    await new Promise((r) => setTimeout(r));
    fixture.detectChanges();
  };
  return { fixture, el, http, cles, bouton, attendre, authentifie: () => authentifie };
}

describe('EtapeAuthentification', () => {
  it('authentifie par la clé d\'accès de l\'appareil', async () => {
    const c = creer(true);
    c.bouton('authentification.utiliserCle')!.click();
    c.http.expectOne(API + 'cles-acces/authentification/options').flush(OPTIONS);
    await c.attendre();
    expect(c.cles.utiliser).toHaveBeenCalledWith(OPTIONS);
    const r = c.http.expectOne(API + 'cles-acces/authentification');
    expect(r.request.body).toEqual({ credentialId: 'c', authenticatorData: 'a', clientDataJSON: 'j', signature: 's' });
    r.flush(null, { status: 204, statusText: 'No Content' });
    await c.attendre();
    expect(c.authentifie()).toBe(1);
  });

  it('se rabat sur le code quand le signataire n\'a pas de clé, puis propose d\'en enregistrer une', async () => {
    const c = creer(true);
    c.bouton('authentification.utiliserCle')!.click();
    c.http.expectOne(API + 'cles-acces/authentification/options').flush({ type: 'https://vinasign.ca/problemes/aucune-cle-acces' },
      { status: 404, statusText: 'x' });
    await c.attendre();
    expect(c.el.querySelector('.message')!.textContent).toContain('authentification.echec');
    c.bouton('authentification.recevoirCode')!.click();
    const demande = c.http.expectOne(API + 'code');
    expect(demande.request.body).toEqual({ canal: 'courriel' });
    demande.flush(null, { status: 202, statusText: 'Accepted' });
    await c.attendre();
    const code = c.el.querySelector<HTMLInputElement>('#code-recu')!;
    expect(c.el.querySelector('label[for="code-recu"]')).not.toBeNull();
    code.value = '000000';
    code.dispatchEvent(new Event('input'));
    c.fixture.detectChanges();
    c.bouton('authentification.verifier')!.click();
    c.http.expectOne(API + 'code/verification').flush({ valide: false, essaisRestants: 4 });
    await c.attendre();
    expect(c.el.querySelector('.message')!.textContent).toContain('4');
    c.bouton('authentification.verifier')!.click();
    c.http.expectOne(API + 'code/verification').flush({ valide: true, essaisRestants: 4 });
    await c.attendre();
    c.bouton('authentification.enregistrerCle')!.click();
    c.http.expectOne(API + 'cles-acces/enregistrement/options').flush(OPTIONS);
    await c.attendre();
    expect(c.http.expectOne(API + 'cles-acces/enregistrement').request.body).toEqual({ attestationObject: 'o', clientDataJSON: 'j' });
  });

  it('sans clé d\'accès possible, le code suffit ; et l\'enregistrement peut attendre', async () => {
    const c = creer(false);
    expect(c.bouton('authentification.utiliserCle')).toBeUndefined();
    c.bouton('authentification.recevoirCode')!.click();
    c.http.expectOne(API + 'code').flush(null, { status: 202, statusText: 'Accepted' });
    await c.attendre();
    const code = c.el.querySelector<HTMLInputElement>('#code-recu')!;
    code.value = '123456';
    code.dispatchEvent(new Event('input'));
    c.fixture.detectChanges();
    c.bouton('authentification.verifier')!.click();
    c.http.expectOne(API + 'code/verification').flush({ valide: true, essaisRestants: 5 });
    await c.attendre();
    expect(c.authentifie()).toBe(1);

    TestBed.resetTestingModule();
    const d = creer(true);
    d.fixture.componentInstance['proposerCle'].set(true);
    d.fixture.detectChanges();
    d.bouton('authentification.plusTard')!.click();
    expect(d.authentifie()).toBe(1);
  });

  it('nomme l\'erreur connue renvoyée par l\'API', async () => {
    const c = creer(false);
    c.http.match(() => true);
    c.bouton('authentification.recevoirCode')!.click();
    c.http.expectOne(API + 'code').flush({ type: 'https://vinasign.ca/problemes/code-verrouille' }, { status: 409, statusText: 'x' });
    await c.attendre();
    expect(c.el.querySelector('.message')!.textContent).toContain('authentification.echec');
  });

  describe('identité vérifiée exigée (VS-131)', () => {
    const reglages = { ...SUIVI_IDENTITE };
    beforeEach(() => { SUIVI_IDENTITE.intervalleMs = 0; SUIVI_IDENTITE.essais = 2; });
    afterEach(() => Object.assign(SUIVI_IDENTITE, reglages));

    function avecNiveau(niveau: string, methode = 'verification-identite') {
      const c = creer(true);
      c.fixture.componentRef.setInput('niveau', niveau);
      c.fixture.detectChanges();
      c.http.expectOne((r) => r.url.endsWith('/referentiels/niveau-authentification')).flush({ code: 'niveau-authentification', libelle: 'N', langue: 'fr',
        valeurs: [{ code: 'identite-verifiee', libelle: 'Identité', ordre: 3, attributs: { methode } }, { code: 'renforce', libelle: 'R', ordre: 2, attributs: {} }] });
      c.fixture.detectChanges();
      return c;
    }

    async function demarrer(c: ReturnType<typeof creer>) {
      const ouvrir = jasmine.isSpy(window.open) ? (window.open as jasmine.Spy) : spyOn(window, 'open');
      c.bouton('authentification.verifierIdentite')!.click();
      c.http.expectOne(API + 'verification-identite').flush({ verification: 'v1', url: 'https://idv.exemple.test/v?r=x' });
      await c.attendre();
      expect(ouvrir).toHaveBeenCalledWith('https://idv.exemple.test/v?r=x', '_blank', 'noopener');
    }

    async function suivi(c: ReturnType<typeof creer>, reponse: object | null) {
      await new Promise((r) => setTimeout(r, 5));
      const req = c.http.expectOne(API + 'verification-identite/v1');
      if (reponse) req.flush(reponse); else req.flush('', { status: 500, statusText: 'x' });
      await c.attendre();
    }

    it('n\'offre que la vérification, puis suit le fournisseur jusqu\'au succès', async () => {
      const c = avecNiveau('identite-verifiee');
      expect(c.bouton('authentification.recevoirCode')).toBeUndefined();
      expect(c.bouton('authentification.utiliserCle')).toBeUndefined();
      await demarrer(c);
      expect(c.el.querySelector('.message')!.textContent).toContain('authentification.identiteEnCours');
      expect(c.bouton('authentification.verifierIdentite')!.disabled).toBeTrue();
      await suivi(c, { id: 'v1', etat: 'en-cours', methode: null });
      expect(c.authentifie()).toBe(0);
      await suivi(c, { id: 'v1', etat: 'reussie', methode: 'piece' });
      expect(c.authentifie()).toBe(1);
    });

    it('annonce un échec, un délai écoulé ou une erreur de suivi', async () => {
      const c = avecNiveau('identite-verifiee');
      await demarrer(c);
      await suivi(c, { id: 'v1', etat: 'echouee', methode: null });
      expect(c.el.querySelector('.message')!.textContent).toContain('authentification.identiteEchouee');
      expect(c.bouton('authentification.verifierIdentite')!.disabled).toBeFalse();

      TestBed.resetTestingModule();
      const d = avecNiveau('identite-verifiee');
      await demarrer(d);
      await suivi(d, { id: 'v1', etat: 'en-cours', methode: null });
      await suivi(d, { id: 'v1', etat: 'en-cours', methode: null });
      expect(d.el.querySelector('.message')!.textContent).toContain('authentification.identiteDelai');

      TestBed.resetTestingModule();
      const e = avecNiveau('identite-verifiee');
      await demarrer(e);
      await suivi(e, null);
      expect(e.el.querySelector('.message')!.textContent).toContain('authentification.echec');
      e.fixture.destroy();
    });

    it('garde le code et la clé quand le niveau n\'exige pas d\'identité, ou si les niveaux sont illisibles', () => {
      const c = avecNiveau('renforce');
      expect(c.bouton('authentification.verifierIdentite')).toBeUndefined();
      expect(c.bouton('authentification.recevoirCode')).toBeDefined();
      TestBed.resetTestingModule();
      const d = creer(false);
      d.fixture.componentRef.setInput('niveau', 'identite-verifiee');
      d.fixture.detectChanges();
      d.http.expectOne((r) => r.url.endsWith('/referentiels/niveau-authentification')).flush('', { status: 503, statusText: 'x' });
      d.fixture.detectChanges();
      expect(d.bouton('authentification.verifierIdentite')).toBeUndefined();
    });
  });
});

