import { Location } from '@angular/common';
import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { Component, inject, input, OnInit, signal } from '@angular/core';
import { CONFIGURATION, LangueService, TraductionService } from '@vinasign/commun';

/** Forme rendue par les étapes de l'inscription (VS-122). */
export interface VueInscription {
  id: string;
  nomOrganisation: string;
  courriel: string;
  domaine: string | null;
  etape: 'courriel-a-confirmer' | 'domaine-a-prouver' | 'a-valider' | 'acceptee' | 'refusee';
  enregistrementDns: string | null;
  valeurDns: string | null;
}

/**
 * Inscription en libre-service (VS-122). Sans jeton : formulaire de demande. Avec le jeton du lien reçu par courriel (retiré
 * aussitôt de la barre d'adresse) : confirmation, puis enregistrement DNS à publier et sa vérification, puis l'étape atteinte.
 */
@Component({
  selector: 'app-inscription',
  templateUrl: './inscription.html',
  styleUrl: './inscription.css',
})
export class Inscription implements OnInit {
  private readonly http = inject(HttpClient);
  private readonly configuration = inject(CONFIGURATION);
  private readonly location = inject(Location);
  protected readonly langues = inject(LangueService);
  protected readonly i18n = inject(TraductionService);

  /** Jeton du lien (paramètre de requête), lu par la liaison des paramètres de route. */
  readonly jeton = input<string | undefined>(undefined);

  protected readonly organisation = signal('');
  protected readonly courriel = signal('');
  protected readonly domaine = signal('');
  protected readonly envoyee = signal(false);
  protected readonly vue = signal<VueInscription | null>(null);
  protected readonly message = signal('');
  protected readonly occupe = signal(false);
  private jetonLien = '';

  ngOnInit(): void {
    const jeton = this.jeton();
    if (!jeton) return;
    this.jetonLien = jeton;
    this.location.replaceState(this.location.path().split('?')[0]);
    this.etape('consultation', (v) => (v.etape === 'courriel-a-confirmer' ? this.etape('confirmation') : this.vue.set(v)));
  }

  protected demander(): void {
    this.occupe.set(true);
    this.message.set('');
    const corps = { organisation: this.organisation().trim(), courriel: this.courriel().trim(), domaine: this.domaine().trim() || null,
      langue: this.langues.langue() };
    this.http.post(`${this.configuration.apiUrl}/api/v1/signup`, corps).subscribe({
      next: () => { this.envoyee.set(true); this.occupe.set(false); },
      error: (e: HttpErrorResponse) => this.echouer(e),
    });
  }

  protected prouverDomaine(): void {
    this.etape('domain-verification');
  }

  private etape(chemin: string, suite: (v: VueInscription) => void = (v) => this.vue.set(v)): void {
    this.occupe.set(true);
    this.message.set('');
    this.http.post<VueInscription>(`${this.configuration.apiUrl}/api/v1/signup/${chemin}`, { jeton: this.jetonLien }).subscribe({
      next: (v) => { this.occupe.set(false); suite(v); },
      error: (e: HttpErrorResponse) => this.echouer(e),
    });
  }

  /** Le type du problème RFC 9457 choisit le message traduit ; sinon un message général. */
  private echouer(e: HttpErrorResponse): void {
    this.occupe.set(false);
    const type = String(e.error?.type ?? '').split('/').pop() ?? '';
    const cle = `inscription.erreur.${type}`;
    this.message.set(this.i18n.t(cle) === cle ? this.i18n.t('inscription.erreur') : this.i18n.t(cle));
  }
}
