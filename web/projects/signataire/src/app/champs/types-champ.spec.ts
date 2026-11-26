import { TestBed } from '@angular/core/testing';
import { HttpTestingController } from '@angular/common/http/testing';
import { TypesChamp } from './types-champ';
import { fournisseursDeTest } from '../../../../commun/src/test-outils';
import { Champ } from '../api/modeles';

export function champ(id: string, type: string, obligatoire = true): Champ {
  return { id, signataire: 's', type, libelle: `Libellé ${id}`, page: 1, x: 0.1, y: 0.1, largeur: 0.2, hauteur: 0.05, obligatoire };
}

export function typesDeChamp(http: HttpTestingController): void {
  http.expectOne((r) => r.url.endsWith('/api/v1/referentiels/type-champ')).flush({
    code: 'type-champ', libelle: 'Type', langue: 'fr', valeurs: [
      { code: 'signature', libelle: 'Signature', ordre: 1, attributs: { saisie: 'signature' } },
      { code: 'texte', libelle: 'Texte', ordre: 2, attributs: { saisie: 'texte' } },
      { code: 'date', libelle: 'Date', ordre: 3, attributs: { saisie: 'date' } },
      { code: 'case', libelle: 'Case', ordre: 4, attributs: { saisie: 'case' } },
      { code: 'nouveau', libelle: 'Nouveau', ordre: 5, attributs: {} },
    ],
  });
}

describe('TypesChamp', () => {
  beforeEach(() => TestBed.configureTestingModule({ providers: fournisseursDeTest() }));

  it('lit le contrôle de saisie dans le référentiel ; un type sans attribut ou inconnu se saisit comme du texte', () => {
    const types = TestBed.inject(TypesChamp);
    TestBed.tick();
    typesDeChamp(TestBed.inject(HttpTestingController));
    expect(types.saisie(champ('a', 'date'))).toBe('date');
    expect(types.rempliParSignature(champ('a', 'signature'))).toBeTrue();
    expect(types.rempliParSignature(champ('a', 'texte'))).toBeFalse();
    expect(types.saisie(champ('a', 'nouveau'))).toBe('texte');
    expect(types.saisie(champ('a', 'absent'))).toBe('texte');
  });

  it('retombe sur du texte si le référentiel ne se charge pas', () => {
    const types = TestBed.inject(TypesChamp);
    TestBed.tick();
    TestBed.inject(HttpTestingController).expectOne((r) => r.url.endsWith('/type-champ')).flush('', { status: 503, statusText: 'x' });
    expect(types.saisie(champ('a', 'signature'))).toBe('texte');
  });
});
