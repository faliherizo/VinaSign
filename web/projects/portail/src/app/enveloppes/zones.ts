/** Zone placée sur une page : coordonnées normalisées (0 à 1, origine en haut à gauche), indépendantes du zoom (VS-060). */
export interface Zone {
  readonly signataire: string;
  readonly type: string;
  readonly libelle: string;
  readonly page: number;
  readonly x: number;
  readonly y: number;
  readonly largeur: number;
  readonly hauteur: number;
  readonly obligatoire: boolean;
}

export const PAS = 0.01;
/** Plus petite zone : reste une cible de 24 px sur la page d'un téléphone (WCAG 2.5.8). */
export const LARGEUR_MIN = 0.07;
export const HAUTEUR_MIN = 0.055;
export const ACTIONS_MAX = 50;

const borner = (v: number, min: number, max: number) => Math.round(Math.max(min, Math.min(max, v)) * 10000) / 10000;

/** Déplace la zone sans jamais la sortir de la page. */
export function deplacer(z: Zone, x: number, y: number): Zone {
  return { ...z, x: borner(x, 0, 1 - z.largeur), y: borner(y, 0, 1 - z.hauteur) };
}

/** Redimensionne depuis le coin inférieur droit, dans la page et au-dessus de la taille minimale. */
export function redimensionner(z: Zone, largeur: number, hauteur: number): Zone {
  return { ...z, largeur: borner(largeur, Math.min(LARGEUR_MIN, 1 - z.x), 1 - z.x), hauteur: borner(hauteur, Math.min(HAUTEUR_MIN, 1 - z.y), 1 - z.y) };
}

const chevauche = (a: Zone, b: Zone) => a.page === b.page && a.x < b.x + b.largeur && b.x < a.x + a.largeur && a.y < b.y + b.hauteur && b.y < a.y + a.hauteur;

/**
 * Place une nouvelle zone à l'endroit libre le plus proche, en descendant puis en reprenant du haut de la page : deux zones
 * superposées se masqueraient, à l'écran comme sur le document signé. Sans place libre, elle reste où elle a été posée.
 */
export function libre(z: Zone, autres: readonly Zone[]): Zone {
  const pas = z.hauteur + PAS;
  const essais = Math.ceil(1 / pas);
  for (let i = 0; i <= essais; i++) {
    const candidate = deplacer(z, z.x, (z.y + i * pas) % (1 - z.hauteur + PAS));
    if (!autres.some((a) => chevauche(candidate, a))) {
      return candidate;
    }
  }
  return z;
}

/** Historique d'annulation (VS-061) : états immuables, cinquante actions au plus. */
export class Historique<T> {
  private passe: T[] = [];
  private futur: T[] = [];

  noter(avant: T): void {
    this.passe = [...this.passe, avant].slice(-ACTIONS_MAX);
    this.futur = [];
  }

  annuler(courant: T): T {
    const precedent = this.passe.pop();
    if (precedent === undefined) {
      return courant;
    }
    this.futur.push(courant);
    return precedent;
  }

  retablir(courant: T): T {
    const suivant = this.futur.pop();
    if (suivant === undefined) {
      return courant;
    }
    this.passe.push(courant);
    return suivant;
  }

  get annulable(): boolean {
    return this.passe.length > 0;
  }

  get retablissable(): boolean {
    return this.futur.length > 0;
  }
}
