import { TestBed } from '@angular/core/testing';
import { ClesNavigateur } from './cles-navigateur';

const tampon = (...octets: number[]) => new Uint8Array(octets).buffer;
const OPTIONS = { defi: 'AQID', rpId: 'signer.vinasign.test', identifiantUtilisateur: 'BAUG', nomAffiche: 'Gabrielle', clesPermises: ['AQI'], delaiMs: 1000 };

describe('ClesNavigateur', () => {
  let cles: ClesNavigateur;
  beforeEach(() => {
    cles = TestBed.inject(ClesNavigateur);
  });

  it('crée une clé ES256 ou RS256 avec vérification de l\'utilisateur, et rend la réponse en base64url', async () => {
    const creer = spyOn(navigator.credentials, 'create').and.resolveTo({ response: { attestationObject: tampon(251, 255), clientDataJSON: tampon(1) } } as never);
    expect(await cles.creer(OPTIONS)).toEqual({ attestationObject: '-_8', clientDataJSON: 'AQ' });
    const publicKey = (creer.calls.mostRecent().args[0] as CredentialCreationOptions).publicKey!;
    expect(new Uint8Array(publicKey.challenge as ArrayBuffer)).toEqual(new Uint8Array([1, 2, 3]));
    expect(publicKey.pubKeyCredParams.map((p) => p.alg)).toEqual([-7, -257]);
    expect(publicKey.authenticatorSelection!.userVerification).toBe('required');
    expect(publicKey.rp.id).toBe('signer.vinasign.test');
  });

  it('utilise une clé parmi celles permises', async () => {
    const get = spyOn(navigator.credentials, 'get').and.resolveTo({ rawId: tampon(9), response: { authenticatorData: tampon(2), clientDataJSON: tampon(3),
      signature: tampon(4) } } as never);
    expect(await cles.utiliser(OPTIONS)).toEqual({ credentialId: 'CQ', authenticatorData: 'Ag', clientDataJSON: 'Aw', signature: 'BA' });
    const publicKey = (get.calls.mostRecent().args[0] as CredentialRequestOptions).publicKey!;
    expect(new Uint8Array(publicKey.allowCredentials![0].id as ArrayBuffer)).toEqual(new Uint8Array([1, 2]));
    expect(publicKey.userVerification).toBe('required');
  });

  it('sait si l\'appareil prend en charge les clés d\'accès', () => {
    expect(typeof cles.disponible()).toBe('boolean');
  });
});
