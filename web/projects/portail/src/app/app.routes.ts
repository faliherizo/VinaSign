import { Routes } from '@angular/router';
import { Accueil } from './accueil/accueil';
import { Referentiels } from './referentiels/referentiels';
import { Etat } from './etat/etat';
import { Inscription } from './inscription/inscription';
import { Connexion } from './connexion/connexion';
import { Facturation } from './facturation/facturation';
import { exigerRole } from './connexion/connexion.garde';
import { ListeEnveloppes } from './enveloppes/liste-enveloppes';
import { DocumentEnveloppe } from './enveloppes/document-enveloppe';
import { NouvelleEnveloppe } from './enveloppes/nouvelle-enveloppe';

/** Rôles du portail (revendication « roles » du fournisseur d'identité, VS-ADR-009). */
export const ROLES = { administrateur: 'administrateur-organisation', preparateur: 'preparateur', auditeur: 'auditeur' };

export const routes: Routes = [
  { path: '', component: Accueil },
  { path: 'referentiels', component: Referentiels, title: 'referentiels.titre' },
  { path: 'etat', component: Etat, title: 'etat.titre' },
  { path: 'inscription', component: Inscription, title: 'inscription.titre' },
  { path: 'connexion', component: Connexion, title: 'connexion.titre' },
  { path: 'enveloppes', component: ListeEnveloppes, title: 'enveloppes.titre', canActivate: [exigerRole(ROLES.preparateur, ROLES.auditeur)] },
  { path: 'enveloppes/nouvelle', component: NouvelleEnveloppe, title: 'envoi.titre', canActivate: [exigerRole(ROLES.preparateur)] },
  { path: 'enveloppes/:id', component: DocumentEnveloppe, title: 'document.titre', canActivate: [exigerRole(ROLES.preparateur, ROLES.auditeur)] },
  { path: 'facturation', component: Facturation, title: 'facturation.titre', canActivate: [exigerRole(ROLES.administrateur)] },
  { path: '**', redirectTo: '' },
];
