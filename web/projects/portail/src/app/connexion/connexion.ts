import { Component, inject, input } from '@angular/core';
import { TraductionService } from '@vinasign/commun';
import { SessionPortail } from './session-portail';

/** Écran de connexion : dit pourquoi l'écran demandé n'est pas ouvert et lance la connexion chez le fournisseur. */
@Component({
  selector: 'app-connexion',
  template: `
    <section class="vs-carte" aria-labelledby="connexion-titre">
      <h1 id="connexion-titre">{{ i18n.t('connexion.titre') }}</h1>
      @if (!session.estOfferte) {
        <p class="bandeau">{{ i18n.t('connexion.indisponible') }}</p>
      } @else if (motif() === 'role') {
        <p class="bandeau erreur" role="alert">{{ i18n.t('connexion.role') }}</p>
      } @else if (session.utilisateur(); as u) {
        <p>{{ i18n.t('connexion.deja') }} {{ u.nom }}</p>
      } @else {
        <p>{{ i18n.t('connexion.intro') }}</p>
        <p class="aide">{{ i18n.t('connexion.methode') }}</p>
        <button type="button" class="principal" (click)="session.connecter(retour() ?? '/')">{{ i18n.t('connexion.bouton') }}</button>
      }
    </section>
  `,
  styles: `:host { display: block; max-width: var(--vs-largeur-lecture); } section > :last-child { margin-bottom: 0; }`,
})
export class Connexion {
  protected readonly session = inject(SessionPortail);
  protected readonly i18n = inject(TraductionService);
  readonly retour = input<string>();
  readonly motif = input<string>();
}
