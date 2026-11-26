import { Component, inject } from '@angular/core';
import { Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { CONFIGURATION, LangueService, ListeReferentiel, TraductionService } from '@vinasign/commun';
import { SessionPortail } from './connexion/session-portail';
import { ROLES } from './app.routes';

/** Cadre du portail : lien d'évitement, en-tête (langue, personne connectée), navigation selon les rôles, contenu. */
@Component({
  selector: 'app-root',
  imports: [RouterOutlet, RouterLink, RouterLinkActive, ListeReferentiel],
  templateUrl: './app.html',
  styleUrl: './app.css',
})
export class App {
  protected readonly configuration = inject(CONFIGURATION);
  protected readonly langues = inject(LangueService);
  protected readonly i18n = inject(TraductionService);
  protected readonly session = inject(SessionPortail);
  protected readonly roles = ROLES;
  private readonly router = inject(Router);

  protected connecter(): void {
    void this.session.connecter(this.router.url);
  }
}
