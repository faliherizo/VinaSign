import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, tap } from 'rxjs';
import { CONFIGURATION } from '@vinasign/commun';
import { Champ, DemarrageVerification, OptionsCleAcces, SuiviVerification, VerificationCode, VueSession } from './modeles';
import { SessionSignataire } from './session-signataire';

/** Appels de l'interface du signataire ; le jeton du lien voyage dans le corps, jamais dans une URL d'API. */
@Injectable({ providedIn: 'root' })
export class SignataireApi {
  private readonly http = inject(HttpClient);
  private readonly configuration = inject(CONFIGURATION);
  private readonly session = inject(SessionSignataire);

  private url(chemin: string): string {
    return `${this.configuration.apiUrl}/api/v1/signataire/${chemin}`;
  }

  ouvrir(jeton: string): Observable<VueSession> {
    return this.http.post<VueSession>(this.url('ouverture'), { jeton }).pipe(tap((s) => this.session.jeton.set(s.jetonSession)));
  }

  consentir(version: string): Observable<unknown> {
    return this.http.post(this.url('consentement'), { version });
  }

  document(): Observable<ArrayBuffer> {
    return this.http.get(this.url('document'), { responseType: 'arraybuffer' });
  }

  champs(): Observable<Champ[]> {
    return this.http.get<Champ[]>(this.url('champs'));
  }

  signer(signature: { image?: string; nomSaisi?: string; valeurs: Record<string, string> }): Observable<unknown> {
    return this.http.post(this.url('signature'), signature);
  }

  refuser(motif: string | null): Observable<unknown> {
    return this.http.post(this.url('refus'), { motif });
  }

  demanderCode(canal: string): Observable<unknown> {
    return this.http.post(this.url('code'), { canal });
  }

  verifierCode(code: string): Observable<VerificationCode> {
    return this.http.post<VerificationCode>(this.url('code/verification'), { code });
  }

  optionsCleAcces(ceremonie: 'enregistrement' | 'authentification'): Observable<OptionsCleAcces> {
    return this.http.post<OptionsCleAcces>(this.url(`cles-acces/${ceremonie}/options`), {});
  }

  enregistrerCleAcces(reponse: { attestationObject: string; clientDataJSON: string }): Observable<unknown> {
    return this.http.post(this.url('cles-acces/enregistrement'), reponse);
  }

  authentifierCleAcces(reponse: { credentialId: string; authenticatorData: string; clientDataJSON: string; signature: string }): Observable<unknown> {
    return this.http.post(this.url('cles-acces/authentification'), reponse);
  }

  demarrerVerificationIdentite(): Observable<DemarrageVerification> {
    return this.http.post<DemarrageVerification>(this.url('verification-identite'), {});
  }

  suivreVerificationIdentite(id: string): Observable<SuiviVerification> {
    return this.http.get<SuiviVerification>(this.url(`verification-identite/${encodeURIComponent(id)}`));
  }
}
