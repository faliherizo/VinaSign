import { Component, inject } from '@angular/core';
import { TraductionService } from '@vinasign/commun';

/** Page sans lien : rappelle d'ouvrir le lien reçu. */
@Component({
  selector: 'vs-accueil',
  template: `<p class="bandeau">{{ i18n.t('attente') }}</p>`,
})
export class Accueil {
  protected readonly i18n = inject(TraductionService);
}
