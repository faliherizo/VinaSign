import { Injectable, inject } from '@angular/core';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { catchError, map, of, switchMap } from 'rxjs';
import { LangueService, ReferentielService, ValeurReferentiel } from '@vinasign/commun';
import { Champ } from '../api/modeles';

/** Référentiel des types de champ ; l'attribut « saisie » dit quel contrôle présenter. */
export const REFERENTIEL_TYPES_CHAMP = 'type-champ';

/**
 * Contrôle de saisie de chaque champ, lu dans le référentiel des types (VS-052) : texte, date, case, ou « signature »
 * (rempli par l'acte de signer). Un type sans attribut connu se saisit comme du texte.
 */
@Injectable({ providedIn: 'root' })
export class TypesChamp {
  private readonly referentiels = inject(ReferentielService);
  private readonly langues = inject(LangueService);

  private readonly types = toSignal(
    toObservable(this.langues.langue).pipe(
      switchMap((langue) => this.referentiels.referentiel(REFERENTIEL_TYPES_CHAMP, langue).pipe(catchError(() => of(null)))),
      map((r) => r?.valeurs ?? []),
    ),
    { initialValue: [] as readonly ValeurReferentiel[] },
  );

  saisie(champ: Champ): string {
    return this.types().find((t) => t.code === champ.type)?.attributs['saisie'] ?? 'texte';
  }

  rempliParSignature(champ: Champ): boolean {
    return this.saisie(champ) === 'signature';
  }
}
