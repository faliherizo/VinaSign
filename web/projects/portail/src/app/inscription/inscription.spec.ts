import { Location } from '@angular/common';
import { TestBed } from '@angular/core/testing';
import { HttpTestingController } from '@angular/common/http/testing';
import { Inscription, VueInscription } from './inscription';
import { fournisseursDeTest } from '../../../../commun/src/test-outils';

const API = 'https://api.essai/api/v1/signup';

function vue(etape: VueInscription['etape'], dns = false): VueInscription {
  return { id: 'i1', nomOrganisation: 'Nord', courriel: 'rh@exemple.ca', domaine: dns ? 'exemple.ca' : null, etape,
    enregistrementDns: dns ? '_vinasign-verification.exemple.ca' : null, valeurDns: dns ? 'vinasign-abc' : null };
}

describe('Inscription (VS-122)', () => {
  let http: HttpTestingController;
  let location: jasmine.SpyObj<Location>;

  beforeEach(() => spyOnProperty(navigator, 'language', 'get').and.returnValue('fr-CA'));

  function creer(jeton?: string) {
    location = jasmine.createSpyObj<Location>('Location', ['path', 'replaceState']);
    location.path.and.returnValue('/inscription?jeton=' + jeton);
    TestBed.configureTestingModule({ imports: [Inscription], providers: [...fournisseursDeTest(), { provide: Location, useValue: location }] });
    const fixture = TestBed.createComponent(Inscription);
    if (jeton) fixture.componentRef.setInput('jeton', jeton);
    http = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
    http.match((r) => r.url.startsWith('i18n/')).forEach((r) => r.flush({}));
    const el = fixture.nativeElement as HTMLElement;
    const saisir = (id: string, valeur: string) => {
      const champ = el.querySelector<HTMLInputElement>('#' + id)!;
      champ.value = valeur;
      champ.dispatchEvent(new Event('input'));
      fixture.detectChanges();
    };
    return { fixture, el, saisir, rendre: () => fixture.detectChanges() };
  }

  afterEach(() => http.verify());

  it('envoie la demande puis annonce le lien, sans révéler si le courriel existe', () => {
    const c = creer();
    const bouton = c.el.querySelector<HTMLButtonElement>('button[type="submit"]')!;
    expect(bouton.disabled).toBeTrue();
    c.saisir('organisation', ' Nord ');
    c.saisir('courriel', 'rh@exemple.ca');
    expect(bouton.disabled).toBeFalse();
    bouton.click();
    const r = http.expectOne(API);
    expect(r.request.body).toEqual({ organisation: 'Nord', courriel: 'rh@exemple.ca', domaine: null, langue: 'fr' });
    r.flush(null, { status: 202, statusText: 'Accepted' });
    c.rendre();
    expect(c.el.querySelector('[role="status"]')!.textContent).toContain('inscription.envoyee');
    expect(c.el.querySelector('form')).toBeNull();
  });

  it('transmet le domaine et nomme l\'erreur connue', () => {
    const c = creer();
    c.saisir('organisation', 'Nord');
    c.saisir('courriel', 'rh@exemple.ca');
    c.saisir('domaine', 'exemple.ca');
    c.el.querySelector<HTMLButtonElement>('button[type="submit"]')!.click();
    const r = http.expectOne(API);
    expect(r.request.body.domaine).toBe('exemple.ca');
    r.flush({ type: 'https://vinasign.ca/problemes/inscription-fermee' }, { status: 403, statusText: 'x' });
    c.rendre();
    expect(c.el.querySelector('.message')!.textContent).toContain('inscription.erreur');
  });

  it('confirme le lien, retire le jeton de l\'adresse, puis fait prouver le domaine', () => {
    const c = creer('jeton-1');
    expect(location.replaceState).toHaveBeenCalledWith('/inscription');
    const consultation = http.expectOne(API + '/consultation');
    expect(consultation.request.body).toEqual({ jeton: 'jeton-1' });
    consultation.flush(vue('courriel-a-confirmer', true));
    http.expectOne(API + '/confirmation').flush(vue('domaine-a-prouver', true));
    c.rendre();
    expect(c.el.querySelector('.dns')!.textContent).toContain('_vinasign-verification.exemple.ca');
    expect(c.el.querySelector('.dns')!.textContent).toContain('vinasign-abc');
    const verifier = c.el.querySelector<HTMLButtonElement>('section button')!;
    verifier.click();
    http.expectOne(API + '/domain-verification').flush({ type: 'https://vinasign.ca/problemes/domaine-non-prouve' }, { status: 409, statusText: 'x' });
    c.rendre();
    expect(c.el.querySelector('.message')!.textContent).toContain('inscription.erreur');
    verifier.click();
    http.expectOne(API + '/domain-verification').flush(vue('a-valider', true));
    c.rendre();
    expect(c.el.querySelector('[role="status"]')!.textContent).toContain('inscription.etape.a-valider');
  });

  it('affiche l\'étape d\'une demande déjà confirmée, ou l\'erreur d\'un lien invalide', () => {
    const c = creer('jeton-2');
    http.expectOne(API + '/consultation').flush(vue('acceptee'));
    c.rendre();
    expect(c.el.querySelector('[role="status"]')!.textContent).toContain('inscription.etape.acceptee');
    TestBed.resetTestingModule();
    const d = creer('jeton-3');
    http.expectOne(API + '/consultation').flush('', { status: 500, statusText: 'x' });
    d.rendre();
    expect(d.el.querySelector('.message')!.textContent).toContain('inscription.erreur');
    expect(d.el.querySelector('form')).toBeNull();
  });
});
