import { TestBed } from '@angular/core/testing';
import { HttpTestingController } from '@angular/common/http/testing';
import { ReferentielService } from './referentiel.service';
import { fournisseursDeTest } from '../test-outils';

describe('ReferentielService', () => {
  let service: ReferentielService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: fournisseursDeTest() });
    service = TestBed.inject(ReferentielService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('liste les types dans la langue demandée, à la racine configurée', () => {
    service.types('en').subscribe((t) => expect(t.length).toBe(1));
    const r = http.expectOne((req) => req.url === 'https://api.essai/api/v1/referentiels' && req.params.get('langue') === 'en');
    r.flush([{ code: 'x', libelle: 'X', nombreValeurs: 2 }]);
  });

  it('encode le code du référentiel dans le chemin', () => {
    service.referentiel('a/b', 'fr').subscribe((v) => expect(v.code).toBe('a/b'));
    const r = http.expectOne((req) => req.url === 'https://api.essai/api/v1/referentiels/a%2Fb' && req.params.get('langue') === 'fr');
    r.flush({ code: 'a/b', libelle: 'AB', langue: 'fr', valeurs: [] });
  });
});
