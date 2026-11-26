import { DOCUMENT } from '@angular/common';
import { Injectable, inject } from '@angular/core';

/** Événements annoncés à la page qui intègre le parcours ; aucun ne porte de donnée personnelle ni de jeton. */
export type Avis = 'ready' | 'signed' | 'declined' | 'error' | 'closed';

/**
 * Avis à l'application intégratrice (VS-055) par postMessage, à l'origine exacte de la page parente et seulement si
 * l'application l'a déclarée (origines reçues avec la session intégrée) : jamais à « * », jamais hors iframe. L'origine
 * parente est lue dans location.ancestorOrigins, à défaut dans le référent.
 */
@Injectable({ providedIn: 'root' })
export class AvisParent {
  private readonly document = inject(DOCUMENT);
  private declarees: readonly string[] = [];

  /** Origines déclarées par l'application pour cette session ; vide pour un lien d'invitation. */
  autoriser(origines: readonly string[]): void {
    this.declarees = origines;
  }

  signaler(avis: Avis): void {
    const fenetre = this.document.defaultView;
    const origine = this.origineParente();
    if (!fenetre || fenetre.parent === fenetre || !origine || !this.declarees.includes(origine)) {
      return;
    }
    fenetre.parent.postMessage({ source: 'vinasign', avis }, origine);
  }

  private origineParente(): string | null {
    const ancetres = this.document.location?.ancestorOrigins;
    if (ancetres && ancetres.length > 0) {
      return ancetres[0];
    }
    try {
      return this.document.referrer ? new URL(this.document.referrer).origin : null;
    } catch {
      return null;
    }
  }
}
