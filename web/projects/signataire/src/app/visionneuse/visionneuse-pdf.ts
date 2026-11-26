import { Component, ElementRef, effect, inject, input, signal, viewChildren } from '@angular/core';
import { TraductionService } from '@vinasign/commun';
import { Champ } from '../api/modeles';
import { ChargeurPdf, DocumentPdf } from '@vinasign/commun';

/** Une page affichée : numéro, rapport hauteur/largeur, texte extrait pour les lecteurs d'écran. */
interface PageAffichee {
  numero: number;
  texte: string;
}

/**
 * Visionneuse du document (VS-050) : chaque page est dessinée à la largeur disponible (aucun défilement horizontal),
 * son texte est restitué aux technologies d'assistance, et les champs du signataire sont repérés sur la page.
 */
@Component({
  selector: 'vs-visionneuse-pdf',
  templateUrl: './visionneuse-pdf.html',
  styleUrl: './visionneuse-pdf.css',
})
export class VisionneusePdf {
  protected readonly i18n = inject(TraductionService);
  private readonly chargeur = inject(ChargeurPdf);
  private readonly hote = inject(ElementRef<HTMLElement>);
  private readonly toiles = viewChildren<ElementRef<HTMLCanvasElement>>('toile');

  readonly donnees = input.required<ArrayBuffer>();
  readonly champs = input<readonly Champ[]>([]);

  protected readonly pages = signal<readonly PageAffichee[]>([]);
  protected readonly etat = signal<'chargement' | 'pret' | 'erreur'>('chargement');
  private document: DocumentPdf | null = null;

  constructor() {
    effect(() => void this.ouvrir(this.donnees()));
    effect(() => {
      if (this.toiles().length > 0 && this.document) {
        void this.dessiner(this.document);
      }
    });
  }

  protected champsDeLaPage(numero: number): readonly Champ[] {
    return this.champs().filter((c) => c.page === numero);
  }

  /** Affichage progressif : les pages apparaissent dès que le document est ouvert, leur texte suit page par page. */
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

  private async dessiner(doc: DocumentPdf): Promise<void> {
    const largeur = Math.max(320, (this.hote.nativeElement as HTMLElement).clientWidth) * Math.max(1, window.devicePixelRatio);
    for (const [i, toile] of this.toiles().entries()) {
      await doc.rendre(i + 1, toile.nativeElement, largeur);
    }
  }
}
