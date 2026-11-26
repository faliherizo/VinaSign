import { Component, effect, inject, signal } from '@angular/core';
import { LangueService, Referentiel, ReferentielService, TraductionService } from '@vinasign/commun';
import { forkJoin, of, switchMap } from 'rxjs';

/** Tous les référentiels et leurs valeurs, découverts par l'API : l'écran ne connaît aucun code à l'avance. */
@Component({
  selector: 'app-referentiels',
  templateUrl: './referentiels.html',
  styleUrl: './referentiels.css',
})
export class Referentiels {
  private readonly referentiels = inject(ReferentielService);
  private readonly langues = inject(LangueService);
  protected readonly i18n = inject(TraductionService);
  protected readonly liste = signal<readonly Referentiel[]>([]);
  protected readonly erreur = signal(false);

  constructor() {
    effect(() => this.charger(this.langues.langue()));
  }

  private charger(langue: string): void {
    this.referentiels.types(langue).pipe(
      switchMap((types) => (types.length ? forkJoin(types.map((t) => this.referentiels.referentiel(t.code, langue))) : of([]))),
    ).subscribe({
      next: (liste) => { this.liste.set(liste); this.erreur.set(false); },
      error: () => this.erreur.set(true),
    });
  }
}
