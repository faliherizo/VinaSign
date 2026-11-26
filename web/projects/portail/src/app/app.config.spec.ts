import { ApplicationInitStatus } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { configurationApplication, demarrerSession } from './app.config';
import { FauxClientOidc } from '../faux-client-oidc';
import { CONFIGURATION } from '@vinasign/commun';
import { CONFIGURATION_TEST } from '../../../commun/src/test-outils';
import { CLIENT_OIDC } from './connexion/client-oidc';
import { SessionPortail } from './connexion/session-portail';

describe('configurationApplication (portail)', () => {
  type Fournisseur = { provide?: unknown; useValue?: unknown };

  it('injecte la configuration lue à l\'exécution ; sans fournisseur d\'identité, aucun client OIDC', () => {
    const fournisseurs = configurationApplication(CONFIGURATION_TEST).providers as Fournisseur[];
    expect(fournisseurs.find((f) => f.provide === CONFIGURATION)?.useValue).toBe(CONFIGURATION_TEST);
    expect(fournisseurs.find((f) => f.provide === CLIENT_OIDC)?.useValue).toBeNull();
  });

  it('avec un fournisseur d\'identité, crée le client OIDC du portail', () => {
    const configuration = { ...CONFIGURATION_TEST, connexion: { emetteur: 'https://idp.x', client: 'portail', portees: 'openid' } };
    const fournisseurs = configurationApplication(configuration).providers as Fournisseur[];
    expect(fournisseurs.find((f) => f.provide === CLIENT_OIDC)?.useValue).not.toBeNull();
  });
});

describe('démarrage du portail', () => {
  it('tente la session du fournisseur avant la première navigation', async () => {
    const client = new FauxClientOidc();
    TestBed.configureTestingModule({ providers: configurationApplication(CONFIGURATION_TEST, client).providers });
    await TestBed.inject(ApplicationInitStatus).donePromise;
    expect(client.appels).toEqual(['silencieuse']);
  });
});

describe('demarrerSession', () => {
  function historique(): History & { remplacees: string[] } {
    const remplacees: string[] = [];
    return { remplacees, replaceState: (_d: unknown, _t: string, url: string) => remplacees.push(url) } as unknown as History & { remplacees: string[] };
  }

  it('rouvre l\'écran demandé après le retour du fournisseur', async () => {
    const h = historique();
    const session = { demarrer: () => Promise.resolve('/facturation') } as unknown as SessionPortail;
    await demarrerSession(session, { href: 'https://portail.x/connexion/retour?code=c' } as Location, h);
    expect(h.remplacees).toEqual(['/facturation']);
  });

  it('ne touche pas l\'adresse en dehors d\'un retour', async () => {
    const h = historique();
    const session = { demarrer: () => Promise.resolve(null) } as unknown as SessionPortail;
    await demarrerSession(session, { href: 'https://portail.x/etat' } as Location, h);
    expect(h.remplacees).toEqual([]);
  });
});
