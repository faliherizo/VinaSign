import { TestBed } from '@angular/core/testing';
import { HttpTestingController } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { App } from './app';
import { routes } from './app.routes';
import { fournisseursDeTest } from '../../../commun/src/test-outils';
import { CLIENT_OIDC } from './connexion/client-oidc';
import { FauxClientOidc, utilisateur } from '../faux-client-oidc';

describe('App (portail)', () => {
  function cadre(client: FauxClientOidc | null): { el: HTMLElement; detecter: () => void } {
    spyOnProperty(navigator, 'language', 'get').and.returnValue('fr-CA');
    TestBed.configureTestingModule({ imports: [App], providers: [...fournisseursDeTest(), provideRouter(routes), { provide: CLIENT_OIDC, useValue: client }] });
    const fixture = TestBed.createComponent(App);
    const http = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
    http.expectOne('i18n/fr.json').flush({ 'nav.accueil': 'Accueil', evitement: 'Aller au contenu', 'nav.facturation': 'Facturation' });
    http.expectOne((r) => r.url.endsWith('/api/v1/referentiels/langue')).flush({ code: 'langue', libelle: 'Langue', langue: 'fr', valeurs: [] });
    fixture.detectChanges();
    return { el: fixture.nativeElement as HTMLElement, detecter: () => fixture.detectChanges() };
  }

  it('affiche le lien d\'évitement, la navigation traduite et le choix de langue par référentiel', () => {
    const { el } = cadre(null);
    expect(el.querySelector('.evitement')?.textContent).toBe('Aller au contenu');
    expect(el.querySelector('nav a')?.textContent).toBe('Accueil');
    expect(el.querySelector('vs-liste-referentiel label')?.textContent).toBe('Langue');
    expect(el.querySelector('main#contenu')).not.toBeNull();
    expect(el.querySelector('.outils button')).toBeNull();
  });

  it('offre la même aide au pied de chaque écran : l\'état du service', () => {
    const { el } = cadre(null);
    const pied = el.querySelector('footer.pied')!;
    expect(pied.querySelector('a')?.getAttribute('href')).toBe('/etat');
    expect(pied.textContent).toContain('pied.aide');
  });

  it('offre la connexion, puis nomme la personne, n\'ouvre la facturation qu\'à l\'administrateur et permet de se déconnecter', async () => {
    const client = new FauxClientOidc();
    const { el, detecter } = cadre(client);
    (el.querySelector('.outils button') as HTMLButtonElement).click();
    expect(client.appels).toEqual(['connexion /']);
    expect(el.querySelector('a[href="/facturation"]')).toBeNull();
    expect(el.querySelector('a[href="/inscription"]')).not.toBeNull();

    client.chargerUtilisateur(utilisateur(['preparateur'], { name: 'Alix' }));
    detecter();
    expect(el.querySelector('.personne')?.textContent).toBe('Alix');
    expect(el.querySelector('a[href="/facturation"]')).toBeNull();
    expect(el.querySelector('a[href="/inscription"]')).toBeNull();

    client.chargerUtilisateur(utilisateur(['administrateur-organisation'], { name: 'Alix' }));
    detecter();
    expect(el.querySelector('a[href="/facturation"]')?.textContent).toBe('Facturation');
    (el.querySelector('.outils button') as HTMLButtonElement).click();
    await Promise.resolve();
    detecter();
    expect(client.appels).toContain('deconnexion');
    expect(el.querySelector('.personne')).toBeNull();
  });
});
