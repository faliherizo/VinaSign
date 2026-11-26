import { Injectable } from '@angular/core';
import type { TextItem } from 'pdfjs-dist/types/src/display/api';

/** Document PDF ouvert : nombre de pages, rendu d'une page sur une toile, texte d'une page (lecteurs d'écran). */
export interface DocumentPdf {
  readonly nombrePages: number;
  /** Dessine la page (1..n) à la largeur demandée, en pixels physiques ; rend le rapport hauteur/largeur. */
  rendre(page: number, toile: HTMLCanvasElement, largeur: number): Promise<number>;
  texte(page: number): Promise<string>;
  /** Libère le document et son exécuteur. */
  fermer(): Promise<void>;
}

/** Port d'ouverture d'un PDF ; l'implémentation PDF.js est remplaçable en test. */
export abstract class ChargeurPdf {
  abstract ouvrir(donnees: ArrayBuffer): Promise<DocumentPdf>;
}

/**
 * Implémentation PDF.js (VS-050). Le module et son exécuteur sont servis par l'origine de l'interface (CSP « self ») :
 * aucun script tiers, aucun JavaScript embarqué dans le PDF n'est exécuté (isEvalSupported faux).
 */
@Injectable()
export class ChargeurPdfJs extends ChargeurPdf {
  async ouvrir(donnees: ArrayBuffer): Promise<DocumentPdf> {
    const pdfjs = await import('pdfjs-dist');
    pdfjs.GlobalWorkerOptions.workerSrc = new URL('pdfjs/pdf.worker.min.mjs', document.baseURI).href;
    const doc = await pdfjs.getDocument({ data: new Uint8Array(donnees), isEvalSupported: false, enableXfa: false }).promise;
    return {
      nombrePages: doc.numPages,
      async rendre(numero, toile, largeur) {
        const page = await doc.getPage(numero);
        const base = page.getViewport({ scale: 1 });
        const vue = page.getViewport({ scale: largeur / base.width });
        toile.width = Math.floor(vue.width);
        toile.height = Math.floor(vue.height);
        await page.render({ canvasContext: toile.getContext('2d')!, viewport: vue }).promise;
        return vue.height / vue.width;
      },
      fermer: () => doc.destroy(),
      async texte(numero) {
        const contenu = await (await doc.getPage(numero)).getTextContent();
        return contenu.items.filter((i): i is TextItem => 'str' in i).map((i) => i.str).join(' ').replace(/\s+/g, ' ').trim();
      },
    };
  }
}
