import { Injectable } from '@angular/core';
import { OptionsCleAcces } from '../api/modeles';

const versB64 = (o: ArrayBuffer) => btoa(String.fromCharCode(...new Uint8Array(o))).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
const depuisB64 = (s: string) => Uint8Array.from(atob(s.replace(/-/g, '+').replace(/_/g, '/')), (c) => c.charCodeAt(0));

/**
 * Accès aux clés d'accès du navigateur (WebAuthn, VS-043), isolé pour être remplacé en test. Algorithmes ES256 et RS256 ;
 * vérification de l'utilisateur exigée (empreinte, visage, NIP de l'appareil).
 */
@Injectable({ providedIn: 'root' })
export class ClesNavigateur {
  disponible(): boolean {
    return typeof window !== 'undefined' && 'PublicKeyCredential' in window && !!navigator.credentials;
  }

  async creer(o: OptionsCleAcces): Promise<{ attestationObject: string; clientDataJSON: string }> {
    const c = (await navigator.credentials.create({ publicKey: {
      challenge: depuisB64(o.defi), rp: { id: o.rpId, name: 'VinaSign' },
      user: { id: depuisB64(o.identifiantUtilisateur), name: o.nomAffiche, displayName: o.nomAffiche },
      pubKeyCredParams: [{ type: 'public-key', alg: -7 }, { type: 'public-key', alg: -257 }],
      authenticatorSelection: { userVerification: 'required', residentKey: 'preferred' }, timeout: o.delaiMs, attestation: 'none',
    } })) as PublicKeyCredential;
    const r = c.response as AuthenticatorAttestationResponse;
    return { attestationObject: versB64(r.attestationObject), clientDataJSON: versB64(r.clientDataJSON) };
  }

  async utiliser(o: OptionsCleAcces): Promise<{ credentialId: string; authenticatorData: string; clientDataJSON: string; signature: string }> {
    const c = (await navigator.credentials.get({ publicKey: {
      challenge: depuisB64(o.defi), rpId: o.rpId, userVerification: 'required', timeout: o.delaiMs,
      allowCredentials: o.clesPermises.map((id) => ({ type: 'public-key' as const, id: depuisB64(id) })),
    } })) as PublicKeyCredential;
    const r = c.response as AuthenticatorAssertionResponse;
    return { credentialId: versB64(c.rawId), authenticatorData: versB64(r.authenticatorData), clientDataJSON: versB64(r.clientDataJSON),
      signature: versB64(r.signature) };
  }
}
