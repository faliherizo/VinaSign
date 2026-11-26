import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { CONFIGURATION } from '@vinasign/commun';
import { CLIENT_OIDC } from './client-oidc';
import { LECTURE_ROLES, SessionPortail } from './session-portail';
import { FauxClientOidc, utilisateur } from '../../faux-client-oidc';

describe('SessionPortail', () => {
  let client: FauxClientOidc;

  function session(c: FauxClientOidc | null = client): SessionPortail {
    TestBed.configureTestingModule({ providers: [{ provide: CLIENT_OIDC, useValue: c }] });
    return TestBed.inject(SessionPortail);
  }

  beforeEach(() => (client = new FauxClientOidc()));

  it('sans fournisseur configuré : rien n\'est offert, rien n\'est tenté', async () => {
    const s = session(null);
    expect(s.estOfferte).toBeFalse();
    expect(await s.demarrer('https://portail.x/facturation')).toBeNull();
    await s.connecter('/facturation');
    await s.deconnecter();
    expect(s.jeton()).toBeNull();
    expect(s.aLeRole('preparateur')).toBeFalse();
  });

  it('sans client injecté (écrans publics) : rien n\'est offert', () => {
    TestBed.configureTestingModule({});
    expect(TestBed.inject(SessionPortail).estOfferte).toBeFalse();
  });

  it('au retour du fournisseur : termine la connexion et rend le chemin demandé', async () => {
    client.retour = () => Promise.resolve(utilisateur(['administrateur-organisation', 7], { name: 'Alix' }, '/facturation?x=1'));
    const s = session();
    expect(await s.demarrer('https://portail.x/connexion/retour?code=c&state=s')).toBe('/facturation?x=1');
    expect(client.appels).toEqual(['retour https://portail.x/connexion/retour?code=c&state=s']);
    expect(s.utilisateur()).toEqual({ nom: 'Alix', roles: ['administrateur-organisation'] });
    expect(s.jeton()).toMatch(/^jeton-/);
    expect(s.aLeRole('administrateur-organisation')).toBeTrue();
    expect(s.aLeRole('preparateur')).toBeFalse();
  });

  it('refuse de rouvrir une adresse externe après la connexion (redirection ouverte)', async () => {
    for (const etat of ['//pirate.x/', 'https://pirate.x', '/\\pirate.x', undefined]) {
      client.retour = () => Promise.resolve(utilisateur([], {}, etat));
      expect(await session().demarrer('https://portail.x/connexion/retour?code=c')).withContext(String(etat)).toBe('/');
      TestBed.resetTestingModule();
    }
  });

  it('un retour invalide (état falsifié, code expiré) ramène à l\'accueil sans connecter', async () => {
    const s = session();
    expect(await s.demarrer('https://portail.x/connexion/retour?code=c&state=faux')).toBe('/');
    expect(s.utilisateur()).toBeNull();
  });

  it('ailleurs : tente la session du fournisseur sans bloquer, et la garde peut l\'attendre', async () => {
    client.silencieux = () => Promise.resolve(utilisateur('pas-une-liste', { preferred_username: 'alix' }));
    const s = session();
    expect(await s.demarrer('https://portail.x/facturation')).toBeNull();
    await s.pret();
    expect(s.utilisateur()).toEqual({ nom: 'alix', roles: [] });
  });

  it('un échec silencieux laisse la personne déconnectée', async () => {
    const s = session();
    await s.demarrer('https://portail.x/');
    await s.pret();
    expect(client.appels).toEqual(['silencieuse']);
    expect(s.utilisateur()).toBeNull();
  });

  it('suit les événements du fournisseur : renouvellement, déconnexion, expiration', () => {
    const s = session();
    client.chargerUtilisateur(utilisateur(['auditeur'], { email: 'a@x.ca' }));
    expect(s.utilisateur()?.nom).toBe('a@x.ca');
    client.dechargerUtilisateur();
    expect(s.utilisateur()).toBeNull();
    client.chargerUtilisateur(utilisateur([], { name: '' }));
    expect(s.utilisateur()?.nom).toBe('');
    client.expirerJeton();
    expect(s.jeton()).toBeNull();
  });

  it('connecte en mémorisant un chemin interne seulement', async () => {
    const s = session();
    await s.connecter('/facturation');
    await s.connecter('https://pirate.x');
    expect(client.appels).toEqual(['connexion /facturation', 'connexion /']);
  });

  it('déconnecte : oublie le jeton puis ferme la session du fournisseur, même s\'il ne répond pas', async () => {
    client.deconnexion = () => Promise.reject(new Error('hors ligne'));
    const s = session();
    client.chargerUtilisateur(utilisateur(['preparateur']));
    await s.deconnecter();
    expect(s.utilisateur()).toBeNull();
    expect(client.appels).toEqual(['oubli', 'deconnexion']);
  });

  describe('rôles attribués par VinaSign (GET /api/v1/moi)', () => {
    function avecRoles(lecture: (jeton: string) => Promise<readonly string[]>): SessionPortail {
      TestBed.configureTestingModule({ providers: [{ provide: CLIENT_OIDC, useValue: client }, { provide: LECTURE_ROLES, useValue: lecture }] });
      return TestBed.inject(SessionPortail);
    }

    it('au retour du fournisseur, les rôles du serveur priment sur le jeton, avant que la garde ne décide', async () => {
      const jetons: string[] = [];
      client.retour = () => Promise.resolve(utilisateur([], { name: 'Léa' }, '/enveloppes'));
      const s = avecRoles((j) => { jetons.push(j); return Promise.resolve(['preparateur', 'auditeur']); });
      expect(await s.demarrer('https://portail.x/connexion/retour?code=c')).toBe('/enveloppes');
      await s.pret();
      expect(jetons).toEqual([s.jeton()!]);
      expect(s.utilisateur()).toEqual({ nom: 'Léa', roles: ['preparateur', 'auditeur'] });
      expect(s.aLeRole('preparateur')).toBeTrue();
    });

    it('si le serveur ne répond pas, les rôles du jeton restent', async () => {
      const s = avecRoles(() => Promise.reject(new Error('hors ligne')));
      client.chargerUtilisateur(utilisateur(['auditeur']));
      await s.pret();
      expect(s.utilisateur()?.roles).toEqual(['auditeur']);
    });

    it('une réponse tardive pour une personne déjà partie ne lui est pas attribuée', async () => {
      let rendre: (r: readonly string[]) => void = () => undefined;
      const s = avecRoles(() => new Promise((ok) => (rendre = ok)));
      client.chargerUtilisateur(utilisateur([]));
      client.dechargerUtilisateur();
      rendre(['preparateur']);
      await s.pret();
      expect(s.utilisateur()).toBeNull();
      client.chargerUtilisateur(utilisateur(['auditeur']));
      expect(s.aLeRole('preparateur')).toBeFalse();
    });

    it('la lecture interroge /api/v1/moi avec le jeton, sans passer par les intercepteurs', async () => {
      TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting(),
        { provide: CONFIGURATION, useValue: { apiUrl: 'https://api.x', languePrincipale: 'fr', referentielLangue: 'langue' } }] });
      const lecture = TestBed.inject(LECTURE_ROLES)!;
      const http = TestBed.inject(HttpTestingController);
      const roles = lecture('jeton-1');
      const requete = http.expectOne('https://api.x/api/v1/moi');
      expect(requete.request.headers.get('Authorization')).toBe('Bearer jeton-1');
      requete.flush({ roles: ['preparateur', 7] });
      expect(await roles).toEqual(['preparateur']);
      const sansRoles = lecture('jeton-2');
      http.expectOne('https://api.x/api/v1/moi').flush({});
      expect(await sansRoles).toEqual([]);
      http.verify();
    });

    it('sans configuration d\'API, aucune lecture : les rôles du jeton seulement', () => {
      TestBed.configureTestingModule({});
      expect(TestBed.inject(LECTURE_ROLES)).toBeNull();
    });
  });
});
