import { TestBed } from '@angular/core/testing';
import { HttpTestingController } from '@angular/common/http/testing';
import { fournisseursDeTest } from '../../../../commun/src/test-outils';
import { EnveloppesService } from './enveloppes.service';

const API = 'https://api.essai/api/v1/envelopes';

describe('EnveloppesService', () => {
  let service: EnveloppesService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: fournisseursDeTest() });
    service = TestBed.inject(EnveloppesService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('liste avec filtre et curseur seulement s\'ils sont donnés', () => {
    service.lister('', null, 25).subscribe();
    expect(http.expectOne(`${API}?limite=25`).request.method).toBe('GET');
    service.lister('envoyee', 'c1', 10).subscribe();
    http.expectOne(`${API}?limite=10&statut=envoyee&apres=c1`);
  });

  it('crée avec une clé d\'idempotence, dépose le PDF sur l\'URL présignée, définit les signataires et envoie', () => {
    service.creer({ externalReference: 'R', mode: 'document', expiresAt: 'x' }, 'cle-1').subscribe();
    expect(http.expectOne(API).request.headers.get('Idempotency-Key')).toBe('cle-1');
    service.demanderDepot('e1').subscribe();
    expect(http.expectOne(`${API}/e1/documents`).request.method).toBe('POST');
    const pdf = new Blob(['%PDF'], { type: 'application/pdf' });
    service.deposer('https://api.essai/api/v1/depots/j', pdf).subscribe();
    const depot = http.expectOne('https://api.essai/api/v1/depots/j');
    expect(depot.request.method).toBe('PUT');
    expect(depot.request.body).toBe(pdf);
    expect(depot.request.headers.get('Content-Type')).toBe('application/pdf');
    service.confirmerDepot('e1', 'd1').subscribe();
    http.expectOne(`${API}/e1/documents/d1/confirmation`);
    service.definirSignataires('e1', []).subscribe();
    expect(http.expectOne(`${API}/e1/signers`).request.method).toBe('PUT');
    service.envoyer('e1').subscribe();
    expect(http.expectOne(`${API}/e1/send`).request.method).toBe('POST');
  });
});
