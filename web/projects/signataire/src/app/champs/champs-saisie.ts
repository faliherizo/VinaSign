import { Component, computed, inject, input, model } from '@angular/core';
import { TraductionService } from '@vinasign/commun';
import { Champ } from '../api/modeles';
import { TypesChamp } from './types-champ';

/**
 * Champs à remplir par le signataire (VS-052), présentés selon le contrôle de leur type ({@link TypesChamp}) ; les
 * champs remplis par l'acte de signer sont seulement annoncés.
 */
@Component({
  selector: 'vs-champs-saisie',
  templateUrl: './champs-saisie.html',
  styleUrl: './champs-saisie.css',
})
export class ChampsSaisie {
  protected readonly i18n = inject(TraductionService);
  private readonly types = inject(TypesChamp);

  readonly champs = input.required<readonly Champ[]>();
  /** Valeurs saisies, par identifiant de champ. */
  readonly valeurs = model<Readonly<Record<string, string>>>({});
  /** Identifiants des champs signalés manquants par la dernière tentative. */
  readonly manquants = input<readonly string[]>([]);

  protected readonly aSaisir = computed(() => this.champs().filter((c) => !this.types.rempliParSignature(c)));
  protected readonly parSignature = computed(() => this.champs().filter((c) => this.types.rempliParSignature(c)));

  protected saisie(champ: Champ): string {
    return this.types.saisie(champ);
  }
  protected valeur(champ: Champ): string {
    return this.valeurs()[champ.id] ?? '';
  }

  protected modifier(champ: Champ, valeur: string): void {
    this.valeurs.set({ ...this.valeurs(), [champ.id]: valeur });
  }

  protected cocher(champ: Champ, coche: boolean): void {
    this.modifier(champ, coche ? 'true' : '');
  }

  protected manquant(champ: Champ): boolean {
    return this.manquants().includes(champ.id);
  }
}
