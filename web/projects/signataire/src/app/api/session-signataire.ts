import { HttpInterceptorFn } from '@angular/common/http';
import { Injectable, inject, signal } from '@angular/core';

/** Jeton de la session ouverte par le lien ; jamais conservé au-delà de la page (aucun stockage du navigateur). */
@Injectable({ providedIn: 'root' })
export class SessionSignataire {
  readonly jeton = signal<string | null>(null);
}

/** Chaque appel à l'API du signataire porte la session dans l'en-tête X-Session-Signataire. */
export const intercepteurSession: HttpInterceptorFn = (requete, suivant) => {
  const jeton = inject(SessionSignataire).jeton();
  if (!jeton || !requete.url.includes('/api/v1/signataire/')) {
    return suivant(requete);
  }
  return suivant(requete.clone({ setHeaders: { 'X-Session-Signataire': jeton } }));
};
