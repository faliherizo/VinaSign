import { TestBed } from '@angular/core/testing';
import { HttpTestingController } from '@angular/common/http/testing';
import { TraductionService } from './traduction.service';
import { LangueService } from './langue.service';
import { fournisseursDeTest } from '../test-outils';

describe('TraductionService', () => {
  let http: HttpTestingController;
  let i18n: TraductionService;
  let langues: LangueService;

  beforeEach(() => {
    spyOnProperty(navigator, 'language', 'get').and.returnValue('en-CA');
    TestBed.configureTestingModule({ providers: fournisseursDeTest() });
    http = TestBed.inject(HttpTestingController);
    i18n = TestBed.inject(TraductionService);
    langues = TestBed.inject(LangueService);
    TestBed.tick();
  });

  afterEach(() => http.verify());

  it('charge les textes de la langue courante ; une clé absente reste visible', () => {
    http.expectOne('i18n/en.json').flush({ titre: 'Title' });
    expect(i18n.t('titre')).toBe('Title');
    expect(i18n.t('absente')).toBe('absente');
  });

  it('recharge quand la langue change', () => {
    http.expectOne('i18n/en.json').flush({ titre: 'Title' });
    langues.choisir('fr');
    TestBed.tick();
    http.expectOne('i18n/fr.json').flush({ titre: 'Titre' });
    expect(i18n.t('titre')).toBe('Titre');
  });

  it('retombe sur la langue principale si la langue n\'a pas de fichier, sans boucler', () => {
    http.expectOne('i18n/en.json').flush('', { status: 404, statusText: 'absent' });
    http.expectOne('i18n/fr.json').flush('', { status: 404, statusText: 'absent' });
    expect(i18n.t('titre')).toBe('titre');
  });
});
