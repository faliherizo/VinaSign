import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpTestingController } from '@angular/common/http/testing';
import { Router, provideRouter } from '@angular/router';
import { ChargeurPdf } from '@vinasign/commun';
import { fournisseursDeTest } from '../../../../commun/src/test-outils';
import { CLIENT_OIDC } from '../connexion/client-oidc';
import { FauxClientOidc, utilisateur } from '../../faux-client-oidc';
import { DocumentEnveloppe } from './document-enveloppe';
import { ChargeurFactice } from './placement-zones.spec';
import { zone } from './zones.spec';

const E = 'https://api.essai/api/v1/envelopes/e1';

describe('DocumentEnveloppe (écran)', () => {
  let http: HttpTestingController;
  let f: ComponentFixture<DocumentEnveloppe>;
  let el: HTMLElement;
  let navigation: jasmine.Spy;

  function ecran(roles = ['preparateur']): void {
    spyOnProperty(navigator, 'language', 'get').and.returnValue('fr-CA');
    const client = new FauxClientOidc();
    TestBed.configureTestingModule({ imports: [DocumentEnveloppe], providers: [...fournisseursDeTest(), provideRouter([]),
      { provide: CLIENT_OIDC, useValue: client }, { provide: ChargeurPdf, useClass: ChargeurFactice }] });
    f = TestBed.createComponent(DocumentEnveloppe);
    client.chargerUtilisateur(utilisateur(roles));
    navigation = spyOn(TestBed.inject(Router), 'navigate').and.resolveTo(true);
    http = TestBed.inject(HttpTestingController);
    f.componentRef.setInput('id', 'e1');
    f.detectChanges();
    http.match('https://api.essai/api/v1/moi').forEach((r) => r.flush({ roles }));
    http.expectOne('i18n/fr.json').flush({});
    el = f.nativeElement as HTMLElement;
  }

  function repondre(statut: string, natures: string[]): void {
    http.expectOne(E).flush({ id: 'e1', referenceExterne: 'DOSSIER-1', statut, signataires: [{ id: 's1', nom: 'Zoé' }], documents: natures.map((nature) => ({ nature })) });
    http.expectOne(E + '/fields').flush([zone()]);
    http.expectOne((r) => r.url.endsWith('/referentiels/type-champ')).flush({ code: 'type-champ', libelle: 'Type', langue: 'fr',
      valeurs: [{ code: 'signature', libelle: 'Signature', ordre: 1, attributs: {} }] });
    f.detectChanges();
  }

  async function document(nature: string): Promise<void> {
    http.expectOne(`${E}/documents/${nature}`).flush(new ArrayBuffer(8));
    f.detectChanges();
    await f.whenStable();
    f.detectChanges();
  }

  const bouton = (libelle: string) => Array.from(el.querySelectorAll('button')).find((b) => b.textContent!.includes(libelle))!;

  it('en brouillon, enregistre les zones placées puis envoie', async () => {
    ecran();
    repondre('brouillon', ['original']);
    await document('original');
    expect(el.querySelectorAll('.zone').length).toBe(1);
    bouton('placement.enregistrer').click();
    const put = http.expectOne(E + '/fields');
    expect(put.request.body).toEqual([zone()]);
    put.flush([]);
    f.detectChanges();
    expect(el.textContent).toContain('placement.enregistre');
    bouton('envoi.envoyer').click();
    http.expectOne(E + '/fields').flush([]);
    http.expectOne(E + '/send').flush({});
    expect(navigation).toHaveBeenCalledWith(['/enveloppes'], { queryParams: { envoyee: 'e1' } });
  });

  it('un refus rend la main et dit que le brouillon est conservé', async () => {
    ecran();
    repondre('brouillon', ['original']);
    await document('original');
    bouton('envoi.envoyer').click();
    http.expectOne(E + '/fields').flush({}, { status: 422, statusText: 'Refusé' });
    f.detectChanges();
    expect(el.querySelector('[role=alert]')!.textContent).toContain('placement.echec');
    expect(bouton('envoi.envoyer').disabled).toBeFalse();
  });

  it('terminée, montre le document final en lecture seule, téléchargeable', async () => {
    ecran();
    repondre('terminee', ['original', 'final']);
    await document('final');
    expect(el.textContent).toContain('document.nature.final');
    expect(el.querySelector('a[download]')!.getAttribute('download')).toBe('DOSSIER-1.pdf');
    expect(el.querySelector('a[download]')!.getAttribute('href')).toMatch(/^blob:/);
    expect(el.querySelectorAll('.zone').length).withContext('les signatures se voient, sans cadre par-dessus').toBe(0);
    expect(bouton('envoi.envoyer')).toBeUndefined();
    f.destroy();
  });

  it('l\'auditeur relit un brouillon sans pouvoir le modifier', async () => {
    ecran(['auditeur']);
    repondre('brouillon', ['original']);
    await document('original');
    expect(bouton('placement.enregistrer')).toBeUndefined();
  });

  it('dit qu\'il n\'y a pas encore de document, ou que l\'enveloppe ou le document ne se charge pas', () => {
    ecran();
    repondre('brouillon', []);
    expect(el.textContent).toContain('document.aucun');
    f.componentInstance.ngOnInit();
    repondre('brouillon', ['original']);
    http.expectOne(E + '/documents/original').flush(new ArrayBuffer(0), { status: 404, statusText: 'Absent' });
    f.detectChanges();
    expect(el.querySelector('[role=alert]')!.textContent).toContain('document.erreur');
    f.componentInstance.ngOnInit();
    http.expectOne(E).flush({}, { status: 404, statusText: 'Absent' });
    f.detectChanges();
    expect(el.querySelector('[role=alert]')!.textContent).toContain('document.erreurEnveloppe');
  });
});
