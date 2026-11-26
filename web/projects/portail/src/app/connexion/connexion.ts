import { Component, inject, input } from '@angular/core';
import { TraductionService } from '@vinasign/commun';
import { SessionPortail } from './session-portail';

/** Écran de connexion : dit pourquoi l'écran demandé n'est pas ouvert et lance la connexion chez le fournisseur. */
@Component({
  selector: 'app-connexion',
  template: `
    <h1>{{ i18n.t('connexion.titre') }}</h1>
    @if (!session.estOfferte) {
      <p>{{ i18n.t('connexion.indisponible') }}</p>
    } @else if (motif() === 'role') {
      <p role="alert">{{ i18n.t('connexion.role') }}</p>
    } @else if (session.utilisateur(); as u) {
      <p>{{ i18n.t('connexion.deja') }} {{ u.nom }}</p>
    } @else {
      <p>{{ i18n.t('connexion.intro') }}</p>
      <button type="button" (click)="session.connecter(retour() ?? '/')">{{ i18n.t('connexion.bouton') }}</button>
    }
  `,
  styles: `button { min-height: 44px; min-width: 44px; padding: 0 1rem; }`,
})
export class Connexion {
  protected readonly session = inject(SessionPortail);
  protected readonly i18n = inject(TraductionService);
  readonly retour = input<string>();
  readonly motif = input<string>();
}
