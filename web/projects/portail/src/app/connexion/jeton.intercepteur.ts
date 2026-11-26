import { HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { CONFIGURATION } from '@vinasign/commun';
import { SessionPortail } from './session-portail';

/** Le jeton de l'utilisateur ne part que vers l'API VinaSign : jamais vers une autre origine. */
export const jetonIntercepteur: HttpInterceptorFn = (requete, suivant) => {
  const jeton = inject(SessionPortail).jeton();
  const api = inject(CONFIGURATION).apiUrl + '/api/';
  if (jeton === null || !requete.url.startsWith(api)) {
    return suivant(requete);
  }
  return suivant(requete.clone({ setHeaders: { Authorization: `Bearer ${jeton}` } }));
};
