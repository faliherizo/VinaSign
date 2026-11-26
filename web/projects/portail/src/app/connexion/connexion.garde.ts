import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { SessionPortail } from './session-portail';

/**
 * Ouvre un écran du portail à la personne connectée qui porte au moins un des rôles donnés ; sinon l'écran de connexion
 * dit pourquoi. Commodité d'affichage seulement : le serveur refuse de toute façon (refus par défaut).
 */
export function exigerRole(...roles: string[]): CanActivateFn {
  return async (_route, etat) => {
    const session = inject(SessionPortail);
    const router = inject(Router);
    await session.pret();
    if (session.utilisateur() === null) {
      return router.createUrlTree(['/connexion'], { queryParams: { retour: etat.url } });
    }
    return roles.some((r) => session.aLeRole(r)) || router.createUrlTree(['/connexion'], { queryParams: { motif: 'role' } });
  };
}
