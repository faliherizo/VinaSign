import { UserManager } from 'oidc-client-ts';
import { creerClientOidc } from './client-oidc';

describe('creerClientOidc', () => {
  it('code avec PKCE, retours sur l\'origine du portail, jetons en mémoire, état dans la session de l\'onglet', () => {
    const client = creerClientOidc({ emetteur: 'https://idp.x/realms/v', client: 'portail', portees: 'openid profile' }, 'https://portail.x');
    const reglages = (client as UserManager).settings;
    expect(reglages.authority).toBe('https://idp.x/realms/v');
    expect(reglages.client_id).toBe('portail');
    expect(reglages.scope).toBe('openid profile');
    expect(reglages.response_type).toBe('code');
    expect(reglages.redirect_uri).toBe('https://portail.x/connexion/retour');
    expect(reglages.silent_redirect_uri).toBe('https://portail.x/connexion-silencieuse.html');
    expect(reglages.post_logout_redirect_uri).toBe('https://portail.x/');
    expect(reglages.automaticSilentRenew).toBeTrue();
    expect(reglages.disablePKCE).toBeFalse();
    const cle = 'essai-' + Math.random();
    void reglages.userStore.set(cle, 'x');
    expect(localStorage.getItem('oidc.' + cle)).toBeNull();
    expect(sessionStorage.getItem('oidc.' + cle)).toBeNull();
  });
});
