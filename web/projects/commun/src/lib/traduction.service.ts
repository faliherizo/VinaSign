import { HttpClient } from '@angular/common/http';
import { Injectable, effect, inject, signal } from '@angular/core';
import { CONFIGURATION } from './configuration';
import { LangueService } from './langue.service';

/**
 * Textes de l'interface, chargés depuis /i18n/{langue}.json quand la langue change. Une langue sans fichier retombe sur
 * la langue principale ; une clé absente s'affiche telle quelle, ce qui la rend visible en recette.
 */
@Injectable({ providedIn: 'root' })
export class TraductionService {
  private readonly http = inject(HttpClient);
  private readonly configuration = inject(CONFIGURATION);
  private readonly langues = inject(LangueService);
  private readonly textes = signal<Readonly<Record<string, string>>>({});

  constructor() {
    effect(() => this.charger(this.langues.langue()));
  }

  t(cle: string): string {
    return this.textes()[cle] ?? cle;
  }

  private charger(langue: string): void {
    this.http.get<Record<string, string>>(`i18n/${langue}.json`).subscribe({
      next: (textes) => this.textes.set(textes),
      error: () => {
        if (langue !== this.configuration.languePrincipale) {
          this.charger(this.configuration.languePrincipale);
        }
      },
    });
  }
}
