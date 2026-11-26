import { TestBed } from '@angular/core/testing';
import { PadSignature, SignatureProduite } from './pad-signature';
import { fournisseursDeTest } from '../../../../commun/src/test-outils';

function creer() {
  TestBed.configureTestingModule({ imports: [PadSignature], providers: fournisseursDeTest() });
  const fixture = TestBed.createComponent(PadSignature);
  const emis: (SignatureProduite | null)[] = [];
  fixture.componentInstance.changement.subscribe((s) => emis.push(s));
  fixture.detectChanges();
  return { fixture, emis, el: fixture.nativeElement as HTMLElement };
}

function pointeur(type: string, x: number, y: number): PointerEvent {
  return new PointerEvent(type, { clientX: x, clientY: y, pointerId: 1, bubbles: true });
}

describe('PadSignature', () => {
  it('produit une image PNG quand on dessine, et rien quand on lève le doigt sans tracer', () => {
    const { el, emis } = creer();
    const toile = el.querySelector('canvas')!;
    const r = toile.getBoundingClientRect();
    toile.dispatchEvent(pointeur('pointerup', r.left, r.top));
    expect(emis).toEqual([]);
    toile.dispatchEvent(pointeur('pointermove', r.left + 5, r.top + 5));
    toile.dispatchEvent(pointeur('pointerdown', r.left + 5, r.top + 5));
    toile.dispatchEvent(pointeur('pointerup', r.left + 5, r.top + 5));
    expect(emis).toEqual([]);
    toile.dispatchEvent(pointeur('pointerdown', r.left + 10, r.top + 10));
    toile.dispatchEvent(pointeur('pointermove', r.left + 60, r.top + 40));
    toile.dispatchEvent(pointeur('pointerleave', r.left + 60, r.top + 40));
    expect(emis.length).toBe(1);
    expect(emis[0]!.image).toMatch(/^iVBOR/);
    expect(emis[0]!.image).not.toContain('data:');
  });

  it('efface le tracé et retire la signature', () => {
    const { el, emis, fixture } = creer();
    const toile = el.querySelector('canvas')!;
    const effacer = [...el.querySelectorAll('button')].find((b) => b.textContent!.includes('effacer'))!;
    expect(effacer.disabled).toBeTrue();
    toile.dispatchEvent(pointeur('pointerdown', 1, 1));
    toile.dispatchEvent(pointeur('pointermove', 30, 30));
    toile.dispatchEvent(pointeur('pointerup', 30, 30));
    fixture.detectChanges();
    expect(effacer.disabled).toBeFalse();
    effacer.click();
    fixture.detectChanges();
    expect(emis.at(-1)).toBeNull();
    expect(effacer.disabled).toBeTrue();
  });

  it('offre au clavier la saisie du nom, sans espace superflu, et rien pour un nom vide', () => {
    const { el, emis, fixture } = creer();
    const radios = el.querySelectorAll<HTMLButtonElement>('[role="radio"]');
    expect(radios[0].getAttribute('aria-checked')).toBe('true');
    radios[1].click();
    fixture.detectChanges();
    expect(radios[1].getAttribute('aria-checked')).toBe('true');
    const champ = el.querySelector<HTMLInputElement>('#nom-tape')!;
    expect(el.querySelector('label[for="nom-tape"]')).not.toBeNull();
    champ.value = '  Marie Tremblay ';
    champ.dispatchEvent(new Event('input'));
    expect(emis.at(-1)).toEqual({ nomSaisi: 'Marie Tremblay' });
    champ.value = '   ';
    champ.dispatchEvent(new Event('input'));
    expect(emis.at(-1)).toBeNull();
    radios[0].click();
    fixture.detectChanges();
    expect(el.querySelector('#nom-tape')).toBeNull();
  });
});
