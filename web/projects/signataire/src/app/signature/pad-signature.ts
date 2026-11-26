import { Component, ElementRef, inject, output, signal, viewChild } from '@angular/core';
import { TraductionService } from '@vinasign/commun';

/** Signature produite : image PNG (base64, sans préfixe) ou nom tapé. */
export interface SignatureProduite {
  image?: string;
  nomSaisi?: string;
}

/**
 * Pavé de signature (VS-051) : dessin au doigt, au stylet ou à la souris (Pointer Events), effaçable ; alternative au
 * clavier : taper son nom. Seule l'image est conservée : ni vitesse, ni pression, ni points horodatés.
 */
@Component({
  selector: 'vs-pad-signature',
  templateUrl: './pad-signature.html',
  styleUrl: './pad-signature.css',
})
export class PadSignature {
  protected readonly i18n = inject(TraductionService);
  private readonly toile = viewChild.required<ElementRef<HTMLCanvasElement>>('toile');
  readonly changement = output<SignatureProduite | null>();

  protected readonly mode = signal<'dessin' | 'saisie'>('dessin');
  protected readonly dessinee = signal(false);
  protected readonly nom = signal('');
  private trace = false;

  protected choisir(mode: 'dessin' | 'saisie'): void {
    this.mode.set(mode);
    this.effacer();
    this.nom.set('');
    this.changement.emit(null);
  }

  protected commencer(e: PointerEvent): void {
    const c = this.contexte();
    this.trace = true;
    (e.target as Element).setPointerCapture?.(e.pointerId);
    c.beginPath();
    c.moveTo(...this.point(e));
  }

  protected tracer(e: PointerEvent): void {
    if (!this.trace) {
      return;
    }
    const c = this.contexte();
    c.lineTo(...this.point(e));
    c.stroke();
    this.dessinee.set(true);
  }

  protected terminer(): void {
    if (!this.trace) {
      return;
    }
    this.trace = false;
    if (this.dessinee()) {
      this.changement.emit({ image: this.toile().nativeElement.toDataURL('image/png').replace(/^data:image\/png;base64,/, '') });
    }
  }

  protected effacer(): void {
    const t = this.toile().nativeElement;
    this.contexte().clearRect(0, 0, t.width, t.height);
    this.dessinee.set(false);
    this.changement.emit(null);
  }

  protected saisir(valeur: string): void {
    this.nom.set(valeur);
    this.changement.emit(valeur.trim() ? { nomSaisi: valeur.trim() } : null);
  }

  private contexte(): CanvasRenderingContext2D {
    const c = this.toile().nativeElement.getContext('2d')!;
    c.lineWidth = 2.5;
    c.lineCap = 'round';
    c.strokeStyle = '#1a1a1a';
    return c;
  }

  /** Coordonnées dans la toile, quelle que soit sa taille affichée. */
  private point(e: PointerEvent): [number, number] {
    const t = this.toile().nativeElement;
    const r = t.getBoundingClientRect();
    return [((e.clientX - r.left) * t.width) / Math.max(r.width, 1), ((e.clientY - r.top) * t.height) / Math.max(r.height, 1)];
  }
}
