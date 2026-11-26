import { TestBed } from '@angular/core/testing';
import { Accueil } from './accueil';
import { fournisseursDeTest } from '../../../commun/src/test-outils';

describe('Accueil', () => {
  it('invite à ouvrir le lien reçu', () => {
    TestBed.configureTestingModule({ imports: [Accueil], providers: fournisseursDeTest() });
    const fixture = TestBed.createComponent(Accueil);
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('attente');
  });
});
