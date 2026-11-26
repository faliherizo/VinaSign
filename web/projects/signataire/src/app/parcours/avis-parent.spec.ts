import { DOCUMENT } from '@angular/common';
import { TestBed } from '@angular/core/testing';
import { AvisParent } from './avis-parent';

interface Contexte {
  referrer?: string;
  ancetres?: string[];
  iframe?: boolean;
  fenetre?: boolean;
}

function avec({ referrer = '', ancetres, iframe = true, fenetre = true }: Contexte) {
  const postMessage = jasmine.createSpy('postMessage');
  const vue: { parent?: unknown } = {};
  vue.parent = iframe ? { postMessage } : vue;
  const location = ancetres ? { ancestorOrigins: Object.assign([...ancetres], { contains: () => false, item: () => null }) } : {};
  TestBed.configureTestingModule({ providers: [{ provide: DOCUMENT, useValue: { referrer, location, defaultView: fenetre ? vue : null } }] });
  const avis = TestBed.inject(AvisParent);
  avis.autoriser(['https://rh.exemple.ca']);
  return { avis, postMessage };
}

describe('AvisParent', () => {
  it('avise la page parente déclarée, à son origine exacte (ancestorOrigins), sans donnée personnelle', () => {
    const { avis, postMessage } = avec({ ancetres: ['https://rh.exemple.ca'], referrer: 'https://autre.ca/' });
    avis.signaler('signed');
    expect(postMessage).toHaveBeenCalledOnceWith({ source: 'vinasign', avis: 'signed' }, 'https://rh.exemple.ca');
  });

  it('retombe sur le référent quand ancestorOrigins manque ou est vide', () => {
    for (const ancetres of [undefined, []]) {
      TestBed.resetTestingModule();
      const { avis, postMessage } = avec({ ancetres, referrer: 'https://rh.exemple.ca/dossiers/42?x=1' });
      avis.signaler('ready');
      expect(postMessage).toHaveBeenCalledOnceWith({ source: 'vinasign', avis: 'ready' }, 'https://rh.exemple.ca');
    }
  });

  it('se tait pour une origine non déclarée, hors iframe, sans origine lisible ou sans fenêtre', () => {
    const cas: Contexte[] = [
      { ancetres: ['https://intrus.ca'] },
      { referrer: 'https://rh.exemple.ca/', iframe: false },
      { referrer: '' },
      { referrer: 'pas une url' },
      { referrer: 'https://rh.exemple.ca/', fenetre: false },
    ];
    for (const c of cas) {
      TestBed.resetTestingModule();
      const { avis, postMessage } = avec(c);
      avis.signaler('ready');
      expect(postMessage).not.toHaveBeenCalled();
    }
  });

  it('se tait pour un lien d\'invitation (aucune origine déclarée)', () => {
    const { avis, postMessage } = avec({ ancetres: ['https://rh.exemple.ca'] });
    avis.autoriser([]);
    avis.signaler('signed');
    expect(postMessage).not.toHaveBeenCalled();
  });
});
