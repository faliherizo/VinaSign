import { Component, signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpTestingController } from '@angular/common/http/testing';
import { ListeReferentiel } from './liste-referentiel';
import { fournisseursDeTest } from '../test-outils';

@Component({
  imports: [ListeReferentiel],
  template: `<vs-liste-referentiel type="essai" [(valeur)]="choix" [messageErreur]="message()" [libelleVide]="vide()" [attributRequis]="attribut()" />`,
})
class Hote {
  choix = '';
  message = signal('Liste indisponible');
  vide = signal('');
  attribut = signal('');
}

describe('ListeReferentiel', () => {
  let fixture: ComponentFixture<Hote>;
  let http: HttpTestingController;
  const VALEURS = [
    { code: 'b', libelle: 'Bé', ordre: 1, attributs: {} },
    { code: 'a', libelle: 'A', ordre: 2, attributs: {} },
  ];

  function creer(choix = '', vide = ''): void {
    spyOnProperty(navigator, 'language', 'get').and.returnValue('fr-CA');
    TestBed.configureTestingModule({ imports: [Hote], providers: fournisseursDeTest() });
    fixture = TestBed.createComponent(Hote);
    fixture.componentInstance.choix = choix;
    fixture.componentInstance.vide.set(vide);
    http = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
  }

  function repondre(): void {
    http.expectOne((r) => r.url.endsWith('/api/v1/referentiels/essai') && r.params.get('langue') === 'fr')
      .flush({ code: 'essai', libelle: 'Essai', langue: 'fr', valeurs: VALEURS });
    fixture.detectChanges();
  }

  const select = () => fixture.nativeElement.querySelector('select') as HTMLSelectElement;

  it('remplit la liste et son libellé depuis l\'API, dans l\'ordre reçu', () => {
    creer();
    repondre();
    expect(fixture.nativeElement.querySelector('label').textContent).toBe('Essai');
    expect(select().getAttribute('id')).toBe(fixture.nativeElement.querySelector('label').getAttribute('for'));
    expect(Array.from(select().options).map((o) => o.textContent)).toEqual(['Bé', 'A']);
    expect(fixture.componentInstance.choix).toBe('b', 'première valeur choisie par défaut');
    expect(select().getAttribute('aria-busy')).toBe('false');
  });

  it('ne choisit rien quand le référentiel est vide', () => {
    creer();
    http.expectOne(() => true).flush({ code: 'essai', libelle: 'Essai', langue: 'fr', valeurs: [] });
    fixture.detectChanges();
    expect(select().options.length).toBe(0);
    expect(fixture.componentInstance.choix).toBe('');
  });

  it('garde la valeur fournie et remonte le choix de l\'utilisateur', () => {
    creer('a');
    repondre();
    expect(fixture.componentInstance.choix).toBe('a');
    select().value = 'b';
    select().dispatchEvent(new Event('change'));
    expect(fixture.componentInstance.choix).toBe('b');
  });

  it('annonce une erreur de chargement, avec le message de l\'appelant ou à défaut le code', () => {
    creer();
    http.expectOne(() => true).flush('', { status: 500, statusText: 'panne' });
    fixture.detectChanges();
    const alerte = fixture.nativeElement.querySelector('[role="alert"]') as HTMLElement;
    expect(alerte.textContent).toBe('Liste indisponible');
    expect(select().getAttribute('aria-describedby')).toBe(alerte.id);
  });

  it('retombe sur le code du référentiel si aucun message n\'est fourni', () => {
    creer();
    fixture.componentInstance.message.set('');
    fixture.detectChanges();
    http.expectOne(() => true).flush('', { status: 500, statusText: 'panne' });
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('[role="alert"]').textContent).toBe('essai');
  });

  it('offre une première option sans valeur, choisie par défaut, quand l\'appelant la nomme', () => {
    creer('', 'Tous');
    repondre();
    const options = Array.from(select().options).map((o) => o.textContent?.trim());
    expect(options).toEqual(['Tous', 'Bé', 'A']);
    expect(select().value).toBe('');
    expect(fixture.componentInstance.choix).toBe('');
    select().value = 'a';
    select().dispatchEvent(new Event('change'));
    expect(fixture.componentInstance.choix).toBe('a');
  });

  it('ne garde que les valeurs dont l\'attribut demandé vaut true, et choisit la première d\'entre elles', () => {
    spyOnProperty(navigator, 'language', 'get').and.returnValue('fr-CA');
    TestBed.configureTestingModule({ imports: [Hote], providers: fournisseursDeTest() });
    fixture = TestBed.createComponent(Hote);
    fixture.componentInstance.attribut.set('exigeCourriel');
    http = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
    http.expectOne((r) => r.url.endsWith('/api/v1/referentiels/essai')).flush({ code: 'essai', libelle: 'Essai', langue: 'fr', valeurs: [
      { code: 'b', libelle: 'Bé', ordre: 1, attributs: {} }, { code: 'a', libelle: 'A', ordre: 2, attributs: { exigeCourriel: 'true' } }] });
    fixture.detectChanges();
    expect(Array.from(select().options).map((o) => o.value)).toEqual(['a']);
    expect(fixture.componentInstance.choix).toBe('a');
  });
});
