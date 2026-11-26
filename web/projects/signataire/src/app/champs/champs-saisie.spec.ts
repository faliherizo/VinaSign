import { TestBed } from '@angular/core/testing';
import { HttpTestingController } from '@angular/common/http/testing';
import { ChampsSaisie } from './champs-saisie';
import { fournisseursDeTest } from '../../../../commun/src/test-outils';
import { champ, typesDeChamp } from './types-champ.spec';

function creer(champs = [champ('t', 'texte'), champ('d', 'date', false), champ('c', 'case'), champ('s', 'signature')]) {
  TestBed.configureTestingModule({ imports: [ChampsSaisie], providers: fournisseursDeTest() });
  const fixture = TestBed.createComponent(ChampsSaisie);
  fixture.componentRef.setInput('champs', champs);
  fixture.detectChanges();
  typesDeChamp(TestBed.inject(HttpTestingController));
  fixture.detectChanges();
  return { fixture, el: fixture.nativeElement as HTMLElement };
}

describe('ChampsSaisie', () => {
  it('présente chaque champ avec le contrôle de son type et un libellé associé', () => {
    const { el } = creer();
    expect(el.querySelector<HTMLInputElement>('#champ-t')!.type).toBe('text');
    expect(el.querySelector<HTMLInputElement>('#champ-d')!.type).toBe('date');
    expect(el.querySelector<HTMLInputElement>('#champ-c')!.type).toBe('checkbox');
    expect(el.querySelector('#champ-s')).toBeNull();
    for (const id of ['t', 'd', 'c']) {
      expect(el.querySelector(`label[for="champ-${id}"]`)).not.toBeNull();
    }
    expect(el.querySelector<HTMLInputElement>('#champ-t')!.required).toBeTrue();
    expect(el.querySelector<HTMLInputElement>('#champ-d')!.required).toBeFalse();
    expect(el.textContent).toContain('champs.parSignature');
  });

  it('remonte les valeurs saisies et cochées', () => {
    const { el, fixture } = creer();
    const t = el.querySelector<HTMLInputElement>('#champ-t')!;
    t.value = 'Québec';
    t.dispatchEvent(new Event('input'));
    const c = el.querySelector<HTMLInputElement>('#champ-c')!;
    c.click();
    expect(fixture.componentInstance.valeurs()).toEqual({ t: 'Québec', c: 'true' });
    c.click();
    fixture.detectChanges();
    expect(fixture.componentInstance.valeurs()['c']).toBe('');
    expect(c.checked).toBeFalse();
  });

  it('signale chaque champ manquant, relié à son message', () => {
    const { el, fixture } = creer();
    fixture.componentRef.setInput('manquants', ['t', 'c']);
    fixture.detectChanges();
    const t = el.querySelector('#champ-t')!;
    expect(t.getAttribute('aria-invalid')).toBe('true');
    expect(el.querySelector(`#${t.getAttribute('aria-describedby')}`)!.textContent).toContain('champs.manquant');
    expect(el.querySelector('#champ-c')!.getAttribute('aria-describedby')).toBe('erreur-c');
    expect(el.querySelector('#champ-d')!.getAttribute('aria-describedby')).toBeNull();
  });

  it('n\'affiche rien sans champ à saisir', () => {
    const { el } = creer([]);
    expect(el.querySelector('fieldset')).toBeNull();
    expect(el.textContent!.trim()).toBe('');
  });
});
