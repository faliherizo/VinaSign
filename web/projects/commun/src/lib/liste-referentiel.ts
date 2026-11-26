import { Component, effect, inject, input, model, signal } from '@angular/core';
import { LangueService } from './langue.service';
import { ValeurReferentiel } from './referentiel';
import { ReferentielService } from './referentiel.service';

let rang = 0;

/**
 * Liste déroulante remplie par un référentiel de l'API : aucune option n'est écrite dans un gabarit. Une valeur ajoutée
 * en base apparaît au prochain chargement ; le libellé de la liste vient lui aussi du référentiel, dans la langue courante.
 * La liste n'apparaît qu'avec son libellé (WCAG 4.1.2) : le code du référentiel le remplace si le chargement échoue.
 */
@Component({
  selector: 'vs-liste-referentiel',
  template: `
    @if (libelle() || erreur()) {
      <label [for]="identifiant">{{ libelle() || type() }}</label>
      <select [id]="identifiant" [value]="valeur()" (change)="choisir($event)" [attr.aria-busy]="chargement()" [attr.aria-describedby]="erreur() ? identifiant + '-erreur' : null">
        @if (libelleVide()) {
          <option value="" [selected]="!valeur()">{{ libelleVide() }}</option>
        }
        @for (option of options(); track option.code) {
          <option [value]="option.code" [selected]="option.code === valeur()">{{ option.libelle }}</option>
        }
      </select>
    } @else {
      <span class="attente" aria-hidden="true"></span>
    }
    @if (erreur()) {
      <p class="erreur" role="alert" [id]="identifiant + '-erreur'">{{ erreur() }}</p>
    }
  `,
  styles: `:host { display: flex; flex-direction: column; gap: .25rem; } select { min-height: 44px; }
    .attente { display: block; height: calc(1.55em + 44px + .25rem); }`,
})
export class ListeReferentiel {
  private readonly referentiels = inject(ReferentielService);
  private readonly langues = inject(LangueService);

  /** Code du référentiel à afficher. */
  readonly type = input.required<string>();
  /** Valeur choisie (code), liée dans les deux sens. */
  readonly valeur = model<string>('');
  /** Message affiché si le référentiel ne se charge pas ; fourni par l'appelant, traduit. */
  readonly messageErreur = input<string>('');
  /** Libellé d'une première option sans valeur (« Tous », « Aucun ») ; absent, la première valeur est choisie d'office. */
  readonly libelleVide = input<string>('');
  /** Ne garde que les valeurs dont cet attribut vaut « true » (ex. exigeCourriel) : le filtre vient des données. */
  readonly attributRequis = input<string>('');

  protected readonly identifiant = `vs-liste-${++rang}`;
  protected readonly options = signal<readonly ValeurReferentiel[]>([]);
  protected readonly libelle = signal('');
  protected readonly chargement = signal(true);
  protected readonly erreur = signal('');

  constructor() {
    effect(() => this.charger(this.type(), this.langues.langue()));
  }

  protected choisir(evenement: Event): void {
    this.valeur.set((evenement.target as HTMLSelectElement).value);
  }

  private charger(type: string, langue: string): void {
    this.chargement.set(true);
    this.referentiels.referentiel(type, langue).subscribe({
      next: (r) => {
        this.libelle.set(r.libelle);
        const attribut = this.attributRequis();
        const valeurs = attribut ? r.valeurs.filter((v) => v.attributs[attribut] === 'true') : r.valeurs;
        this.options.set(valeurs);
        this.erreur.set('');
        if (!this.valeur() && !this.libelleVide() && valeurs.length > 0) {
          this.valeur.set(valeurs[0].code);
        }
        this.chargement.set(false);
      },
      error: () => {
        this.erreur.set(this.messageErreur() || type);
        this.chargement.set(false);
      },
    });
  }
}
