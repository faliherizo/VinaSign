import { Component, ElementRef, OnDestroy, computed, effect, inject, input, model, signal, viewChildren } from '@angular/core';
import { ChargeurPdf, DocumentPdf, TraductionService, ValeurReferentiel } from '@vinasign/commun';
import { Historique, PAS, Zone, deplacer, libre, redimensionner } from './zones';

export interface SignatairePlace {
  readonly id: string;
  readonly nom: string;
}

/** Geste en cours : zone saisie, point de départ et état d'avant, pour n'inscrire qu'une action à l'historique. */
interface Geste {
  rang: number;
  taille: boolean;
  x0: number;
  y0: number;
  cadre: DOMRect;
  avant: readonly Zone[];
}

let rang = 0;

const FLECHES: Readonly<Record<string, readonly [number, number]>> = { ArrowLeft: [-1, 0], ArrowRight: [1, 0], ArrowUp: [0, -1], ArrowDown: [0, 1] };

/**
 * Document affiché page par page, sur lequel le préparateur place les zones de chaque signataire (VS-060, VS-061) :
 * toucher la page pose une zone, la glisser la déplace, sa poignée la redimensionne. Tout se fait aussi au clavier et par
 * boutons, sans glisser (WCAG 2.5.7). En lecture seule, la même vue sert à relire un document.
 */
@Component({
  selector: 'app-placement-zones',
  templateUrl: './placement-zones.html',
  styleUrl: './placement-zones.css',
})
export class PlacementZones implements OnDestroy {
  protected readonly i18n = inject(TraductionService);
  private readonly chargeur = inject(ChargeurPdf);
  private readonly hote = inject<ElementRef<HTMLElement>>(ElementRef);
  private readonly toiles = viewChildren<ElementRef<HTMLCanvasElement>>('toile');

  readonly donnees = input.required<ArrayBuffer>();
  readonly signataires = input<readonly SignatairePlace[]>([]);
  readonly types = input<readonly ValeurReferentiel[]>([]);
  readonly modifiable = input(false);
  readonly zones = model<readonly Zone[]>([]);

  protected readonly ids = `placement-${++rang}`;
  protected readonly etat = signal<'chargement' | 'pret' | 'erreur'>('chargement');
  protected readonly pages = signal<readonly { numero: number; texte: string }[]>([]);
  protected readonly signataire = signal('');
  protected readonly type = signal('');
  protected readonly choisie = signal<number | null>(null);
  protected readonly annulable = signal(false);
  protected readonly retablissable = signal(false);
  protected readonly indexees = computed(() => this.zones().map((zone, rang) => ({ zone, rang })));
  private readonly historique = new Historique<readonly Zone[]>();
  private document: DocumentPdf | null = null;
  private geste: Geste | null = null;

  constructor() {
    effect(() => void this.ouvrir(this.donnees()));
    effect(() => {
      const toiles = this.toiles();
      if (toiles.length > 0 && this.document) {
        void this.dessiner(this.document, toiles);
      }
    });
    effect(() => this.signataire.set(this.signataires()[0]?.id ?? ''));
    effect(() => this.type.set(this.types()[0]?.code ?? ''));
  }

  ngOnDestroy(): void {
    void this.document?.fermer();
  }

  protected valeur(evenement: Event): string {
    return (evenement.target as HTMLSelectElement).value;
  }

  protected nom(id: string): string {
    return this.signataires().find((s) => s.id === id)?.nom ?? '';
  }

  /** Toucher la page pose une zone centrée sous le doigt ; toucher une zone existante ne fait que la choisir. */
  protected poser(evenement: MouseEvent, page: number): void {
    if (!this.modifiable() || (evenement.target as HTMLElement).closest('.zone')) {
      return;
    }
    const cadre = (evenement.currentTarget as HTMLElement).getBoundingClientRect();
    this.ajouter(page, (evenement.clientX - cadre.left) / cadre.width, (evenement.clientY - cadre.top) / cadre.height);
  }

  /** Pose une zone centrée au point donné (au milieu de la page, par défaut : chemin du clavier). */
  protected ajouter(page: number, cx = 0.5, cy = 0.5): void {
    const type = this.types().find((t) => t.code === this.type());
    const nom = this.nom(this.signataire());
    if (type === undefined || nom === '') {
      return;
    }
    const largeur = Number(type.attributs['largeur'] ?? 0.28);
    const hauteur = Number(type.attributs['hauteur'] ?? 0.07);
    const zone = deplacer({ signataire: this.signataire(), type: type.code, libelle: `${type.libelle} — ${nom}`.slice(0, 120), page, x: 0, y: 0,
      largeur, hauteur, obligatoire: type.attributs['saisie'] === 'signature' }, cx - largeur / 2, cy - hauteur / 2);
    this.appliquer([...this.zones(), libre(zone, this.zones())]);
    this.choisie.set(this.zones().length - 1);
  }

  protected bouger(dx: number, dy: number): void {
    this.surChoisie((z) => deplacer(z, z.x + dx * PAS, z.y + dy * PAS));
  }

  protected agrandir(dl: number, dh: number): void {
    this.surChoisie((z) => redimensionner(z, z.largeur + dl * PAS, z.hauteur + dh * PAS));
  }

  protected retirer(): void {
    const rang = this.choisie();
    if (rang !== null) {
      this.appliquer(this.zones().filter((_, i) => i !== rang));
      this.choisie.set(null);
    }
  }

  protected basculerObligatoire(): void {
    this.surChoisie((z) => ({ ...z, obligatoire: !z.obligatoire }));
  }

  /** Flèches : déplacer ; Majuscule et flèches : redimensionner ; Suppr : retirer. */
  protected touche(evenement: KeyboardEvent): void {
    const pas = FLECHES[evenement.key];
    if (!this.modifiable() || (pas === undefined && evenement.key !== 'Delete')) {
      return;
    }
    evenement.preventDefault();
    if (pas === undefined) {
      this.retirer();
    } else if (evenement.shiftKey) {
      this.agrandir(pas[0], pas[1]);
    } else {
      this.bouger(pas[0], pas[1]);
    }
  }

  protected saisir(evenement: PointerEvent, rang: number, taille: boolean): void {
    this.choisie.set(rang);
    if (!this.modifiable()) {
      return;
    }
    evenement.stopPropagation();
    const zone = (evenement.currentTarget as HTMLElement).closest<HTMLElement>('.zone')!;
    zone.focus();
    zone.setPointerCapture(evenement.pointerId);
    this.geste = { rang, taille, x0: evenement.clientX, y0: evenement.clientY, avant: this.zones(),
      cadre: zone.closest('.surface')!.getBoundingClientRect() };
  }

  protected suivre(evenement: PointerEvent): void {
    const g = this.geste;
    if (g === null) {
      return;
    }
    const dx = (evenement.clientX - g.x0) / g.cadre.width;
    const dy = (evenement.clientY - g.y0) / g.cadre.height;
    const depart = g.avant[g.rang];
    const arrivee = g.taille ? redimensionner(depart, depart.largeur + dx, depart.hauteur + dy) : deplacer(depart, depart.x + dx, depart.y + dy);
    this.zones.set(g.avant.map((z, i) => (i === g.rang ? arrivee : z)));
  }

  protected lacher(): void {
    const g = this.geste;
    this.geste = null;
    if (g !== null && this.zones() !== g.avant) {
      this.noter(g.avant);
    }
  }

  protected annuler(): void {
    this.remplacer(this.historique.annuler(this.zones()));
  }

  protected retablir(): void {
    this.remplacer(this.historique.retablir(this.zones()));
  }

  private surChoisie(change: (z: Zone) => Zone): void {
    const rang = this.choisie();
    if (rang !== null) {
      this.appliquer(this.zones().map((z, i) => (i === rang ? change(z) : z)));
    }
  }

  private appliquer(zones: readonly Zone[]): void {
    const avant = this.zones();
    this.zones.set(zones);
    this.noter(avant);
  }

  private remplacer(zones: readonly Zone[]): void {
    this.zones.set(zones);
    this.choisie.set(null);
    this.refleter();
  }

  private noter(avant: readonly Zone[]): void {
    this.historique.noter(avant);
    this.refleter();
  }

  private refleter(): void {
    this.annulable.set(this.historique.annulable);
    this.retablissable.set(this.historique.retablissable);
  }

  /** Les pages s'affichent dès l'ouverture ; leur texte (lecteurs d'écran) suit, page par page. */
  private async ouvrir(donnees: ArrayBuffer): Promise<void> {
    this.etat.set('chargement');
    try {
      const doc = await this.chargeur.ouvrir(donnees);
      this.document = doc;
      this.pages.set(Array.from({ length: doc.nombrePages }, (_, i) => ({ numero: i + 1, texte: '' })));
      this.etat.set('pret');
      for (let n = 1; n <= doc.nombrePages; n++) {
        const texte = await doc.texte(n);
        this.pages.update((pages) => pages.map((p) => (p.numero === n ? { ...p, texte } : p)));
      }
    } catch {
      this.etat.set('erreur');
    }
  }

  private async dessiner(doc: DocumentPdf, toiles: readonly ElementRef<HTMLCanvasElement>[]): Promise<void> {
    const largeur = Math.max(320, this.hote.nativeElement.clientWidth) * Math.max(1, window.devicePixelRatio);
    for (const [i, toile] of toiles.entries()) {
      await doc.rendre(i + 1, toile.nativeElement, largeur);
    }
  }
}
