import { TestBed } from '@angular/core/testing';
import { HttpTestingController } from '@angular/common/http/testing';
import { fournisseursDeTest } from '../../../../commun/src/test-outils';
import { CLIENT_OIDC } from './client-oidc';
import { Connexion } from './connexion';
import { SessionPortail } from './session-portail';
import { FauxClientOidc, utilisateur } from '../../faux-client-oidc';

describe('Connexion (écran)', () => {
  function ecran(client: FauxClientOidc | null, entrees: Record<string, string> = {}, avant: () => void = () => undefined): HTMLElement {
    spyOnProperty(navigator, 'language', 'get').and.returnValue('fr-CA');
    TestBed.configureTestingModule({ imports: [Connexion], providers: [...fournisseursDeTest(), { provide: CLIENT_OIDC, useValue: client }] });
    TestBed.inject(SessionPortail);
    avant();
    const f = TestBed.createComponent(Connexion);
    for (const [cle, valeur] of Object.entries(entrees)) f.componentRef.setInput(cle, valeur);
    f.detectChanges();
    TestBed.inject(HttpTestingController).expectOne('i18n/fr.json').flush({ 'connexion.bouton': 'Se connecter' });
    f.detectChanges();
    return f.nativeElement as HTMLElement;
  }

  it('lance la connexion en mémorisant l\'écran demandé', () => {
    const client = new FauxClientOidc();
    ecran(client, { retour: '/facturation' }).querySelector('button')!.click();
    expect(client.appels).toEqual(['connexion /facturation']);
  });

  it('sans écran demandé, rouvre l\'accueil', () => {
    const client = new FauxClientOidc();
    ecran(client).querySelector('button')!.click();
    expect(client.appels).toEqual(['connexion /']);
  });

  it('rôle manquant : message d\'alerte, pas de bouton', () => {
    const el = ecran(new FauxClientOidc(), { motif: 'role' });
    expect(el.querySelector('[role=alert]')).not.toBeNull();
    expect(el.querySelector('button')).toBeNull();
  });

  it('déjà connecté : nomme la personne', () => {
    const client = new FauxClientOidc();
    expect(ecran(client, {}, () => client.chargerUtilisateur(utilisateur([], { name: 'Alix' }))).textContent).toContain('Alix');
  });

  it('sans fournisseur configuré : le dit, sans bouton', () => {
    expect(ecran(null).querySelector('button')).toBeNull();
  });
});
