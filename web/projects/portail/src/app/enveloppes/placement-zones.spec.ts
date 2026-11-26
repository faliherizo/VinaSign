import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpTestingController } from '@angular/common/http/testing';
import { ChargeurPdf, DocumentPdf } from '@vinasign/commun';
import { fournisseursDeTest } from '../../../../commun/src/test-outils';
import { PlacementZones } from './placement-zones';
import { zone } from './zones.spec';

export class ChargeurFactice extends ChargeurPdf {
  echec = false;
  fermes = 0;
  rendues: number[] = [];
  ouvrir(): Promise<DocumentPdf> {
    return this.echec ? Promise.reject(new Error('illisible')) : Promise.resolve({ nombrePages: 2,
      rendre: async (p, toile) => { this.rendues.push(p); toile.width = 600; toile.height = 800; return 4 / 3; },
      texte: async (p) => `Texte ${p}`, fermer: async () => { this.fermes++; } });
  }
}

const TYPES = [{ code: 'signature', libelle: 'Signature', ordre: 1, attributs: { saisie: 'signature', largeur: '0.28', hauteur: '0.07' } },
  { code: 'initiales', libelle: 'Initiales', ordre: 2, attributs: { saisie: 'signature', largeur: '0.12', hauteur: '0.055' } },
  { code: 'libre', libelle: 'Libre', ordre: 3, attributs: {} }];

describe('PlacementZones', () => {
  let f: ComponentFixture<PlacementZones>;
  let el: HTMLElement;
  let chargeur: ChargeurFactice;

  async function creer(entrees: Record<string, unknown> = {}, echec = false): Promise<void> {
    chargeur = new ChargeurFactice();
    chargeur.echec = echec;
    TestBed.configureTestingModule({ imports: [PlacementZones], providers: [...fournisseursDeTest(), { provide: ChargeurPdf, useValue: chargeur }] });
    f = TestBed.createComponent(PlacementZones);
    const valeurs = { donnees: new ArrayBuffer(4), signataires: [{ id: 's1', nom: 'Zoé' }, { id: 's2', nom: 'Léo' }], types: TYPES, modifiable: true, ...entrees };
    Object.entries(valeurs).forEach(([k, v]) => f.componentRef.setInput(k, v));
    document.body.append(f.nativeElement);
    f.detectChanges();
    TestBed.inject(HttpTestingController).match('i18n/fr.json').forEach((r) => r.flush({}));
    await f.whenStable();
    f.detectChanges();
    await f.whenStable();
    f.detectChanges();
    el = f.nativeElement as HTMLElement;
  }

  afterEach(() => f.nativeElement.remove());

  const zones = () => f.componentInstance.zones();
  const boutons = () => Array.from(el.querySelectorAll<HTMLButtonElement>('button'));
  function cliquer(libelle: string): void {
    boutons().find((b) => (b.getAttribute('aria-label') ?? b.textContent ?? '').trim().startsWith(libelle))!.click();
    f.detectChanges();
  }
  function choisir(rang: number, valeur: string): void {
    const s = el.querySelectorAll('select')[rang];
    s.value = valeur;
    s.dispatchEvent(new Event('change'));
    f.detectChanges();
  }
  function pointeur(cible: Element, type: string, x: number, y: number): void {
    cible.dispatchEvent(new PointerEvent(type, { bubbles: true, clientX: x, clientY: y, pointerId: 1 }));
    f.detectChanges();
  }
  function touche(key: string, shiftKey = false): void {
    el.querySelector('.zone')!.dispatchEvent(new KeyboardEvent('keydown', { key, shiftKey, bubbles: true, cancelable: true }));
    f.detectChanges();
  }

  it('affiche chaque page et son texte, puis libère le document', async () => {
    await creer();
    expect(chargeur.rendues).toEqual([1, 2]);
    expect(el.querySelectorAll('.texte-page')[1].textContent).toBe('Texte 2');
    f.destroy();
    expect(chargeur.fermes).toBe(1);
  });

  it('annonce un document illisible', async () => {
    await creer({}, true);
    expect(el.querySelector('[role=alert]')!.textContent).toContain('document.erreur');
  });

  it('toucher la page pose la zone à cet endroit, pour la personne et le type choisis', async () => {
    await creer();
    choisir(0, 's2');
    choisir(1, 'initiales');
    const surface = el.querySelectorAll('.surface')[1];
    const r = surface.getBoundingClientRect();
    surface.dispatchEvent(new MouseEvent('click', { bubbles: true, clientX: r.left + r.width * 0.5, clientY: r.top + r.height * 0.8 }));
    f.detectChanges();
    expect(zones().length).toBe(1);
    expect(zones()[0]).toEqual(jasmine.objectContaining({ signataire: 's2', type: 'initiales', libelle: 'Initiales — Léo', page: 2, largeur: 0.12, obligatoire: true }));
    expect(zones()[0].x).toBeCloseTo(0.44, 1);
    expect(zones()[0].y).toBeCloseTo(0.77, 1);
    const bouton = el.querySelector<HTMLElement>('.zone')!;
    expect(bouton.getAttribute('aria-label')).toBe('Initiales — Léo, document.page 2');
    expect(bouton.getAttribute('aria-pressed')).toBe('true');
    bouton.click();
    expect(zones().length).withContext('toucher une zone ne la double pas').toBe(1);
  });

  it('au clavier : ajoute à une page, déplace, redimensionne et retire', async () => {
    await creer();
    cliquer('placement.ajouter 2');
    const z = zones()[0];
    expect(z.page).toBe(2);
    touche('ArrowRight');
    touche('ArrowUp');
    expect(zones()[0]).toEqual(jasmine.objectContaining({ x: Math.round((z.x + 0.01) * 1e4) / 1e4, y: Math.round((z.y - 0.01) * 1e4) / 1e4 }));
    touche('ArrowDown', true);
    expect(zones()[0].hauteur).toBeCloseTo(0.08, 4);
    touche('a');
    touche('Delete');
    expect(zones()).toEqual([]);
  });

  it('par boutons, sans glisser : déplace, change la taille, rend facultative, retire ; annule et rétablit', async () => {
    await creer({ zones: [zone()] });
    el.querySelector<HTMLElement>('.zone')!.focus();
    f.detectChanges();
    ['placement.gauche', 'placement.droite', 'placement.haut', 'placement.bas', 'placement.plusLarge', 'placement.moinsLarge', 'placement.plusHaut',
      'placement.moinsHaut'].forEach(cliquer);
    expect(zones()[0]).toEqual(zone());
    el.querySelector<HTMLInputElement>('input[type=checkbox]')!.click();
    expect(zones()[0].obligatoire).toBeFalse();
    f.detectChanges();
    cliquer('placement.retirer');
    expect(zones()).toEqual([]);
    cliquer('placement.annuler');
    expect(zones().length).toBe(1);
    cliquer('placement.annuler');
    expect(zones()[0].obligatoire).toBeTrue();
    cliquer('placement.retablir');
    expect(zones()[0].obligatoire).toBeFalse();
  });

  it('glisser déplace, la poignée redimensionne ; un geste = une action', async () => {
    await creer({ zones: [zone()] });
    const z = el.querySelector<HTMLElement>('.zone')!;
    spyOn(z, 'setPointerCapture');
    const r = el.querySelector('.surface')!.getBoundingClientRect();
    pointeur(z, 'pointermove', 5, 5);
    pointeur(z, 'pointerdown', 100, 100);
    pointeur(z, 'pointermove', 100 + r.width * 0.1, 100);
    pointeur(z, 'pointermove', 100 + r.width * 0.2, 100 + r.height * 0.3);
    pointeur(z, 'pointerup', 0, 0);
    expect(zones()[0].x).toBeCloseTo(0.3, 2);
    expect(zones()[0].y).toBeCloseTo(0.4, 2);
    const p = el.querySelector('.poignee')!;
    pointeur(p, 'pointerdown', 200, 200);
    pointeur(z, 'pointermove', 200 + r.width * 0.1, 200);
    pointeur(z, 'pointerup', 0, 0);
    expect(zones()[0].largeur).toBeCloseTo(0.38, 2);
    pointeur(z, 'pointerup', 0, 0);
    pointeur(z, 'pointerdown', 1, 1);
    pointeur(z, 'pointercancel', 1, 1);
    cliquer('placement.annuler');
    cliquer('placement.annuler');
    expect(zones()[0]).withContext('deux gestes, deux annulations').toEqual(zone());
  });

  it('un type sans taille déclarée a une taille de repli et n\'est pas obligatoire', async () => {
    await creer();
    choisir(1, 'libre');
    cliquer('placement.ajouter 1');
    expect(zones()[0]).toEqual(jasmine.objectContaining({ largeur: 0.28, hauteur: 0.07, obligatoire: false }));
  });

  it('sans signataire, rien ne se pose', async () => {
    await creer({ signataires: [] });
    cliquer('placement.ajouter 1');
    expect(zones()).toEqual([]);
  });

  it('en lecture seule : ni outils, ni pose, ni déplacement', async () => {
    await creer({ zones: [zone()], modifiable: false });
    expect(el.querySelector('.outils')).toBeNull();
    el.querySelector<HTMLElement>('.surface')!.click();
    touche('ArrowRight');
    pointeur(el.querySelector('.zone')!, 'pointerdown', 1, 1);
    expect(zones()).toEqual([zone()]);
  });
});
