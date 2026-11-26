import { chargerConfiguration } from './configuration';

function lecteur(statut: number, corps: unknown): typeof fetch {
  return (() => Promise.resolve(new Response(JSON.stringify(corps), { status: statut }))) as unknown as typeof fetch;
}

describe('chargerConfiguration', () => {
  it('lit config.json et retire la barre finale de l\'URL', async () => {
    const c = await chargerConfiguration(lecteur(200, { apiUrl: 'https://api.x/', languePrincipale: 'fr', referentielLangue: 'langue' }));
    expect(c).toEqual({ apiUrl: 'https://api.x', languePrincipale: 'fr', referentielLangue: 'langue' });
  });

  it('accepte une URL vide (même origine)', async () => {
    const c = await chargerConfiguration(lecteur(200, { apiUrl: '', languePrincipale: 'en', referentielLangue: 'langue' }));
    expect(c.apiUrl).toBe('');
  });

  it('refuse de démarrer sans configuration', async () => {
    await expectAsync(chargerConfiguration(lecteur(404, {}))).toBeRejectedWithError(/introuvable \(404\)/);
  });

  it('refuse une configuration incomplète', async () => {
    await expectAsync(chargerConfiguration(lecteur(200, { apiUrl: 'x' }))).toBeRejectedWithError(/incomplète/);
    await expectAsync(chargerConfiguration(lecteur(200, { languePrincipale: 'fr', referentielLangue: 'l' }))).toBeRejectedWithError(/incomplète/);
  });
});

describe('chargerConfiguration — connexion du portail', () => {
  const base = { apiUrl: '', languePrincipale: 'fr', referentielLangue: 'langue' };

  it('lit le fournisseur d\'identité et retire la barre finale de l\'émetteur', async () => {
    const c = await chargerConfiguration(lecteur(200, { ...base, connexion: { emetteur: 'https://idp.x/realms/v/', client: 'portail', portees: 'openid profile' } }));
    expect(c.connexion).toEqual({ emetteur: 'https://idp.x/realms/v', client: 'portail', portees: 'openid profile' });
  });

  it('sans bloc de connexion, le portail reste public', async () => {
    expect((await chargerConfiguration(lecteur(200, base))).connexion).toBeUndefined();
  });

  it('refuse un bloc de connexion incomplet ou sans openid', async () => {
    for (const connexion of [{}, { emetteur: 'idp.x', client: 'c', portees: 'openid' }, { emetteur: 'https://idp.x', portees: 'openid' },
      { emetteur: 'https://idp.x', client: 'c', portees: 'profile' }, { emetteur: 'https://idp.x', client: 'c' }]) {
      await expectAsync(chargerConfiguration(lecteur(200, { ...base, connexion }))).toBeRejectedWithError(/connexion incomplète/);
    }
  });
});
