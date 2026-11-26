import { Routes } from '@angular/router';
import { Accueil } from './accueil';
import { Parcours } from './parcours/parcours';

/** Lien d'invitation (/s/:jeton) et session intégrée en iframe (/integre/:jeton) ouvrent le même parcours. */
export const routes: Routes = [
  { path: 's/:jeton', component: Parcours },
  { path: 'integre/:jeton', component: Parcours },
  { path: '**', component: Accueil },
];
