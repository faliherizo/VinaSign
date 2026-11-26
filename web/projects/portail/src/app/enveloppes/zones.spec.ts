import { ACTIONS_MAX, HAUTEUR_MIN, Historique, LARGEUR_MIN, Zone, deplacer, libre, redimensionner } from './zones';

export function zone(x = 0.1, y = 0.1): Zone {
  return { signataire: 's1', type: 'signature', libelle: 'Signature — Zoé', page: 1, x, y, largeur: 0.28, hauteur: 0.07, obligatoire: true };
}

describe('Zones', () => {
  it('une zone ne sort jamais de la page', () => {
    expect(deplacer(zone(), -1, 2)).toEqual(jasmine.objectContaining({ x: 0, y: 0.93 }));
    expect(redimensionner(zone(0.8, 0.9), 5, 5)).toEqual(jasmine.objectContaining({ largeur: 0.2, hauteur: 0.1 }));
  });

  it('une zone garde une taille touchable', () => {
    expect(redimensionner(zone(), 0, 0)).toEqual(jasmine.objectContaining({ largeur: LARGEUR_MIN, hauteur: HAUTEUR_MIN }));
  });

  it('une nouvelle zone va à la place libre la plus proche, sur sa page seulement', () => {
    expect(libre(zone(), [])).toEqual(zone());
    expect(libre(zone(), [{ ...zone(), page: 2 }])).toEqual(zone());
    expect(libre(zone(), [zone()]).y).toBeCloseTo(0.18, 4);
    const pleine = Array.from({ length: 30 }, (_, i) => ({ ...zone(0, i / 30), largeur: 1, hauteur: 1 / 30 }));
    expect(libre(zone(0.1, 0.9), pleine)).toEqual(zone(0.1, 0.9));
    expect(libre(zone(0.1, 0.9), [zone(0.1, 0.9)]).y).toBeLessThan(0.9);
  });

  it('l\'historique garde cinquante actions, annule, rétablit, et oublie le futur après une nouvelle action', () => {
    const h = new Historique<number>();
    expect(h.annuler(0)).toBe(0);
    expect(h.retablir(0)).toBe(0);
    for (let i = 0; i < ACTIONS_MAX + 5; i++) {
      h.noter(i);
    }
    let courant = ACTIONS_MAX + 5;
    while (h.annulable) {
      courant = h.annuler(courant);
    }
    expect(courant).toBe(5);
    expect(h.retablir(courant)).toBe(6);
    expect(h.retablissable).toBeTrue();
    h.noter(6);
    expect(h.retablissable).toBeFalse();
  });
});
