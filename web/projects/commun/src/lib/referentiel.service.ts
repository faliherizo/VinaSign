import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { CONFIGURATION } from './configuration';
import { Referentiel, ResumeReferentiel } from './referentiel';

/** Source unique de toute liste de l'interface : les référentiels de l'API. */
@Injectable({ providedIn: 'root' })
export class ReferentielService {
  private readonly http = inject(HttpClient);
  private readonly configuration = inject(CONFIGURATION);

  types(langue: string): Observable<ResumeReferentiel[]> {
    return this.http.get<ResumeReferentiel[]>(`${this.configuration.apiUrl}/api/v1/referentiels`, { params: new HttpParams().set('langue', langue) });
  }

  referentiel(code: string, langue: string): Observable<Referentiel> {
    return this.http.get<Referentiel>(`${this.configuration.apiUrl}/api/v1/referentiels/${encodeURIComponent(code)}`,
      { params: new HttpParams().set('langue', langue) });
  }
}
