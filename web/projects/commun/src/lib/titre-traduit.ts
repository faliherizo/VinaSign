import { Injectable, effect, inject, signal } from '@angular/core';
import { Title } from '@angular/platform-browser';
import { RouterStateSnapshot, TitleStrategy } from '@angular/router';
import { TraductionService } from './traduction.service';

/**
 * Titre de l'onglet (WCAG 2.4.2) : la clé de traduction déclarée par la route, suivie du nom de l'application (clé
 * « titre ») ; retraduit quand la langue change. Une route sans titre ne montre que le nom de l'application.
 */
@Injectable({ providedIn: 'root' })
export class TitreTraduit extends TitleStrategy {
  private readonly i18n = inject(TraductionService);
  private readonly titre = inject(Title);
  private readonly cle = signal<string | undefined>(undefined);

  constructor() {
    super();
    effect(() => this.titre.setTitle(this.composer(this.cle())));
  }

  override updateTitle(etat: RouterStateSnapshot): void {
    this.cle.set(this.buildTitle(etat));
  }

  private composer(cle: string | undefined): string {
    const application = this.i18n.t('titre');
    return cle ? `${this.i18n.t(cle)} — ${application}` : application;
  }
}
