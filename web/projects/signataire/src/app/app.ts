import { Component, inject } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { CONFIGURATION, LangueService, ListeReferentiel, TraductionService } from '@vinasign/commun';

/** Expérience du signataire, servie sur sa propre origine et intégrable en iframe (VS-ADR-004). */
@Component({
  selector: 'app-root',
  imports: [RouterOutlet, ListeReferentiel],
  templateUrl: './app.html',
  styleUrl: './app.css',
})
export class App {
  protected readonly configuration = inject(CONFIGURATION);
  protected readonly langues = inject(LangueService);
  protected readonly i18n = inject(TraductionService);
}
