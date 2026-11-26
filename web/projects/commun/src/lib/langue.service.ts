import { Injectable, inject, signal } from '@angular/core';
import { CONFIGURATION } from './configuration';

/** Langue courante de l'interface : choix de l'utilisateur, sinon langue du navigateur, sinon langue principale. */
@Injectable({ providedIn: 'root' })
export class LangueService {
  private readonly configuration = inject(CONFIGURATION);
  private readonly courante = signal(this.initiale());

  readonly langue = this.courante.asReadonly();

  choisir(code: string): void {
    if (code) {
      this.courante.set(code);
      document.documentElement.lang = code;
    }
  }

  private initiale(): string {
    const navigateur = (navigator.language ?? '').slice(0, 2).toLowerCase();
    return /^[a-z]{2}$/.test(navigateur) ? navigateur : this.configuration.languePrincipale;
  }
}
