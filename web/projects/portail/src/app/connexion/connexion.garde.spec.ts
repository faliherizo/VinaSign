import { TestBed } from '@angular/core/testing';
import { ActivatedRouteSnapshot, Router, RouterStateSnapshot, UrlTree, provideRouter } from '@angular/router';
import { CLIENT_OIDC } from './client-oidc';
import { exigerRole } from './connexion.garde';
import { SessionPortail } from './session-portail';
import { FauxClientOidc, utilisateur } from '../../faux-client-oidc';

describe('exigerRole', () => {
  let client: FauxClientOidc;

  beforeEach(() => {
    client = new FauxClientOidc();
    TestBed.configureTestingModule({ providers: [provideRouter([]), { provide: CLIENT_OIDC, useValue: client }] });
    TestBed.inject(SessionPortail);
  });

  function verifier(...roles: string[]): Promise<boolean | UrlTree> {
    return TestBed.runInInjectionContext(() =>
      exigerRole(...roles)({} as ActivatedRouteSnapshot, { url: '/facturation' } as RouterStateSnapshot)) as Promise<boolean | UrlTree>;
  }

  function adresse(r: boolean | UrlTree): string {
    return TestBed.inject(Router).serializeUrl(r as UrlTree);
  }

  it('personne déconnectée : envoyée à la connexion, avec l\'écran demandé à rouvrir', async () => {
    expect(adresse(await verifier('administrateur-organisation'))).toBe('/connexion?retour=%2Ffacturation');
  });

  it('rôle absent : l\'écran de connexion dit pourquoi', async () => {
    client.chargerUtilisateur(utilisateur(['preparateur']));
    expect(adresse(await verifier('administrateur-organisation', 'auditeur'))).toBe('/connexion?motif=role');
  });

  it('un des rôles suffit', async () => {
    client.chargerUtilisateur(utilisateur(['auditeur']));
    expect(await verifier('administrateur-organisation', 'auditeur')).toBeTrue();
  });
});
