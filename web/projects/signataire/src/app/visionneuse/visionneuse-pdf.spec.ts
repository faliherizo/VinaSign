import { TestBed } from '@angular/core/testing';
import { ChargeurPdf, DocumentPdf } from '@vinasign/commun';
import { VisionneusePdf } from './visionneuse-pdf';
import { fournisseursDeTest } from '../../../../commun/src/test-outils';
import { champ } from '../champs/types-champ.spec';

class ChargeurFactice extends ChargeurPdf {
  rendues: number[] = [];
  echec = false;
  ouvrir(): Promise<DocumentPdf> {
    if (this.echec) {
      return Promise.reject(new Error('illisible'));
    }
    return Promise.resolve({
      nombrePages: 2,
      rendre: async (page) => {
        this.rendues.push(page);
        return 1.3;
      },
      texte: async (page) => `Texte de la page ${page}`,
      fermer: async () => undefined,
    });
  }
}

async function creer(echec = false) {
  const chargeur = new ChargeurFactice();
  chargeur.echec = echec;
  TestBed.configureTestingModule({ imports: [VisionneusePdf], providers: [...fournisseursDeTest(), { provide: ChargeurPdf, useValue: chargeur }] });
  const fixture = TestBed.createComponent(VisionneusePdf);
  fixture.componentRef.setInput('donnees', new ArrayBuffer(4));
  fixture.componentRef.setInput('champs', [champ('a', 'texte'), { ...champ('b', 'texte'), page: 2 }, { ...champ('c', 'texte'), page: 9 }]);
  fixture.detectChanges();
  expect(fixture.nativeElement.querySelector('[role="status"]')).not.toBeNull();
  await fixture.whenStable();
  fixture.detectChanges();
  await fixture.whenStable();
  return { fixture, chargeur, el: fixture.nativeElement as HTMLElement };
}

describe('VisionneusePdf', () => {
  it('dessine chaque page, restitue son texte et repère les champs de la page', async () => {
    const { el, chargeur } = await creer();
    expect(el.querySelectorAll('figure').length).toBe(2);
    expect(chargeur.rendues).toEqual([1, 2]);
    expect(el.querySelector('.texte-page')!.textContent).toBe('Texte de la page 1');
    expect(el.querySelector('figcaption')!.textContent).toContain('1 / 2');
    const reperes = el.querySelectorAll<HTMLElement>('.repere');
    expect(reperes.length).toBe(2);
    expect(reperes[0].style.left).toBe('10%');
    expect(reperes[0].getAttribute('aria-hidden')).toBe('true');
  });

  it('annonce un document illisible', async () => {
    const { el } = await creer(true);
    expect(el.querySelector('[role="alert"]')!.textContent).toContain('document.erreur');
  });
});
