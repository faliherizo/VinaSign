import { TestBed } from '@angular/core/testing';
import { LangueService } from './langue.service';
import { CONFIGURATION_TEST, fournisseursDeTest } from '../test-outils';

describe('LangueService', () => {
  function service(langueNavigateur: string | undefined): LangueService {
    spyOnProperty(navigator, 'language', 'get').and.returnValue(langueNavigateur as string);
    TestBed.configureTestingModule({ providers: fournisseursDeTest() });
    return TestBed.inject(LangueService);
  }

  it('part de la langue du navigateur', () => {
    expect(service('en-CA').langue()).toBe('en');
  });

  it('retombe sur la langue principale si le navigateur ne donne rien d\'exploitable', () => {
    expect(service('').langue()).toBe(CONFIGURATION_TEST.languePrincipale);
  });

  it('retombe aussi sur la langue principale si le navigateur ne renseigne pas sa langue', () => {
    expect(service(undefined).langue()).toBe(CONFIGURATION_TEST.languePrincipale);
  });

  it('change de langue et l\'annonce au document ; un choix vide est ignoré', () => {
    const s = service('fr-CA');
    s.choisir('en');
    expect(s.langue()).toBe('en');
    expect(document.documentElement.lang).toBe('en');
    s.choisir('');
    expect(s.langue()).toBe('en');
  });
});
