import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { HttpTestingController } from '@angular/common/http/testing';
import { Title } from '@angular/platform-browser';
import { Router, TitleStrategy, provideRouter } from '@angular/router';
import { fournisseursDeTest } from '../test-outils';
import { TitreTraduit } from './titre-traduit';

@Component({ template: '' })
class Vide {}

describe('TitreTraduit', () => {
  it('compose le titre de l\'onglet à partir de la clé de la route, puis du seul nom de l\'application', async () => {
    spyOnProperty(navigator, 'language', 'get').and.returnValue('fr-CA');
    TestBed.configureTestingModule({
      providers: [...fournisseursDeTest(), { provide: TitleStrategy, useExisting: TitreTraduit },
        provideRouter([{ path: 'etat', component: Vide, title: 'etat.titre' }, { path: '', component: Vide }])],
    });
    const router = TestBed.inject(Router);
    const titre = TestBed.inject(Title);
    await router.navigateByUrl('/etat');
    TestBed.tick();
    TestBed.inject(HttpTestingController).expectOne('i18n/fr.json').flush({ titre: 'VinaSign', 'etat.titre': 'État du service' });
    TestBed.tick();
    expect(titre.getTitle()).toBe('État du service — VinaSign');
    await router.navigateByUrl('/');
    TestBed.tick();
    expect(titre.getTitle()).toBe('VinaSign');
  });
});
