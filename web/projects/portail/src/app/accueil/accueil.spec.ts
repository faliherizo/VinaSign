import { TestBed } from '@angular/core/testing';
import { HttpTestingController } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { Accueil } from './accueil';
import { fournisseursDeTest } from '../../../../commun/src/test-outils';
import { CLIENT_OIDC } from '../connexion/client-oidc';
import { FauxClientOidc, utilisateur } from '../../faux-client-oidc';

describe('Accueil (vitrine)', () => {
  function ecran(client: FauxClientOidc | null) {
    spyOnProperty(navigator, 'language', 'get').and.returnValue('fr-CA');
    TestBed.configureTestingModule({ imports: [Accueil], providers: [...fournisseursDeTest(), provideRouter([]), { provide: CLIENT_OIDC, useValue: client }] });
    const fixture = TestBed.createComponent(Accueil);
    fixture.detectChanges();
    TestBed.inject(HttpTestingController).expectOne('i18n/fr.json').flush({ 'accueil.titre': 'Faites signer' });
    fixture.detectChanges();
    return { fixture, el: fixture.nativeElement as HTMLElement };
  }

  it('présente le titre, la démonstration sous-titrée et sa transcription, les étapes et les atouts', () => {
    const { el } = ecran(null);
    expect(el.querySelector('h1')!.textContent).toBe('Faites signer');
    const video = el.querySelector('video')!;
    expect(video.getAttribute('poster')).toBe('medias/demo/vinasign-demo.jpg');
    expect(video.querySelector('source')!.getAttribute('src')).toBe('medias/demo/vinasign-demo.mp4');
    expect(video.querySelector('track')!.getAttribute('kind')).toBe('captions');
    expect(el.querySelectorAll('#demo-transcription li').length).toBe(4);
    expect(el.querySelectorAll('.etapes li').length).toBe(4);
    expect(el.querySelectorAll('.atouts li').length).toBe(8);
    expect(el.querySelector('button.principal')).withContext('sans fournisseur configuré, pas de connexion offerte').toBeNull();
  });

  it('une personne non connectée peut se connecter : retour sur ses enveloppes', () => {
    const client = new FauxClientOidc();
    const { el } = ecran(client);
    (el.querySelector('button.principal') as HTMLButtonElement).click();
    expect(client.appels).toEqual(['connexion /enveloppes']);
  });

  it('une personne connectée va directement à ses enveloppes', () => {
    const client = new FauxClientOidc();
    const { fixture, el } = ecran(client);
    client.chargerUtilisateur(utilisateur(['preparateur']));
    fixture.detectChanges();
    expect(el.querySelector('a.principal')!.getAttribute('href')).toBe('/enveloppes');
    expect(el.querySelector('button.principal')).toBeNull();
  });

  it('une vidéo non publiée laisse place à un message, la transcription reste', () => {
    const { fixture, el } = ecran(null);
    el.querySelector('video')!.dispatchEvent(new Event('error'));
    fixture.detectChanges();
    expect(el.querySelector('video')).toBeNull();
    expect(el.querySelector('[role=status]')).not.toBeNull();
    expect(el.querySelectorAll('#demo-transcription li').length).toBe(4);
  });
});
