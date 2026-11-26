import { HttpClient, HttpHeaders, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { CONFIGURATION } from '@vinasign/commun';
import { Observable } from 'rxjs';
import { Zone } from './zones';

/** Résumé rendu par GET /api/v1/envelopes (VS-064). */
export interface ResumeEnveloppe {
  id: string;
  referenceExterne: string;
  mode: string;
  statut: string;
  expireLe: string;
  creeLe: string;
  signataires: number;
  signes: number;
}

export interface PageEnveloppes {
  elements: ResumeEnveloppe[];
  suivant: string | null;
}

export interface NouvelleEnveloppe {
  externalReference: string;
  mode: string;
  expiresAt: string;
}

export interface NouveauSignataire {
  nom: string;
  courriel: string;
  langue: string;
  type: string;
  ordre: number;
}

export interface VueEnveloppe {
  id: string;
  referenceExterne: string;
  statut: string;
}

/** Enveloppe détaillée : ses signataires et les natures de document disponibles. */
export interface DetailEnveloppe extends VueEnveloppe {
  signataires: { id: string; nom: string }[];
  documents: { nature: string }[];
}

export interface VueDepot {
  id: string;
  url: string;
  expireLe: string;
}

/** Appels de l'API des enveloppes, au nom de la personne connectée (le jeton est joint par l'intercepteur). */
@Injectable({ providedIn: 'root' })
export class EnveloppesService {
  private readonly http = inject(HttpClient);
  private readonly api = inject(CONFIGURATION).apiUrl + '/api/v1/envelopes';

  lister(statut: string, apres: string | null, limite: number): Observable<PageEnveloppes> {
    let params = new HttpParams().set('limite', limite);
    params = statut ? params.set('statut', statut) : params;
    params = apres ? params.set('apres', apres) : params;
    return this.http.get<PageEnveloppes>(this.api, { params });
  }

  creer(e: NouvelleEnveloppe, cleIdempotence: string): Observable<VueEnveloppe> {
    return this.http.post<VueEnveloppe>(this.api, e, { headers: new HttpHeaders({ 'Idempotency-Key': cleIdempotence }) });
  }

  demanderDepot(id: string): Observable<VueDepot> {
    return this.http.post<VueDepot>(`${this.api}/${id}/documents`, {});
  }

  /** Dépôt sur l'URL présignée : le fichier part tel quel ; le serveur en lit le vrai type et convertit un Word en PDF. */
  deposer(url: string, fichier: Blob): Observable<void> {
    return this.http.put<void>(url, fichier, { headers: new HttpHeaders({ 'Content-Type': fichier.type || 'application/octet-stream' }) });
  }

  confirmerDepot(id: string, depot: string): Observable<VueEnveloppe> {
    return this.http.post<VueEnveloppe>(`${this.api}/${id}/documents/${depot}/confirmation`, {});
  }

  definirSignataires(id: string, signataires: NouveauSignataire[]): Observable<VueEnveloppe> {
    return this.http.put<VueEnveloppe>(`${this.api}/${id}/signers`, signataires);
  }

  envoyer(id: string): Observable<VueEnveloppe> {
    return this.http.post<VueEnveloppe>(`${this.api}/${id}/send`, {});
  }

  consulter(id: string): Observable<DetailEnveloppe> {
    return this.http.get<DetailEnveloppe>(`${this.api}/${id}`);
  }

  document(id: string, nature: string): Observable<ArrayBuffer> {
    return this.http.get(`${this.api}/${id}/documents/${nature}`, { responseType: 'arraybuffer' });
  }

  champs(id: string): Observable<Zone[]> {
    return this.http.get<Zone[]>(`${this.api}/${id}/fields`);
  }

  definirChamps(id: string, zones: readonly Zone[]): Observable<Zone[]> {
    return this.http.put<Zone[]>(`${this.api}/${id}/fields`, zones.map((z) => ({ signataire: z.signataire, type: z.type, libelle: z.libelle, page: z.page,
      x: z.x, y: z.y, largeur: z.largeur, hauteur: z.hauteur, obligatoire: z.obligatoire })));
  }
}
