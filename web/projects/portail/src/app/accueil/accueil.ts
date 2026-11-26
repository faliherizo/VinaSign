import { Component, inject } from '@angular/core';
import { TraductionService } from '@vinasign/commun';

@Component({
  selector: 'app-accueil',
  template: `<h1>{{ i18n.t('accueil.titre') }}</h1><p>{{ i18n.t('accueil.intro') }}</p>`,
})
export class Accueil {
  protected readonly i18n = inject(TraductionService);
}
