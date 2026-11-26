import { HttpClient } from '@angular/common/http';
import { Component, computed, effect, inject, signal } from '@angular/core';
import { CONFIGURATION, LangueService, Referentiel, ReferentielService, TraductionService, ValeurReferentiel } from '@vinasign/commun';
import { forkJoin } from 'rxjs';

/** Forme rendue par GET /api/v1/availability (VS-124). */
export interface VueEtat {
  impact: string;
  composants: Record<string, string>;
  incidents: VueIncident[];
  le: string;
}

export interface VueIncident {
  id: string;
  titre: Record<string, string>;
  impact: string;
  composants: string[];
  debut: string;
  fin: string | null;
  misesAJour: { phase: string; message: Record<string, string>; le: string }[];
}

/** Référentiels qui donnent leurs libellés aux codes de la page ; l'ordre de l'impact en fait la gravité. */
export const REFERENTIELS_ETAT = { impacts: 'impact-incident', composants: 'composant-service', phases: 'phase-incident', objectifs: 'objectif-service' };

/**
 * Page d'état publique (VS-124) : impact global, état de chaque composant, objectifs de service publiés, incidents en
 * cours et historique. Tout libellé vient d'un référentiel ; les textes des incidents s'affichent dans la langue choisie,
 * sinon dans la première disponible.
 */
@Component({
  selector: 'app-etat',
  templateUrl: './etat.html',
  styleUrl: './etat.css',
})
export class Etat {
  private readonly http = inject(HttpClient);
  private readonly configuration = inject(CONFIGURATION);
  private readonly referentiels = inject(ReferentielService);
  protected readonly langues = inject(LangueService);
  protected readonly i18n = inject(TraductionService);
  protected readonly etat = signal<VueEtat | null>(null);
  protected readonly refs = signal<Record<keyof typeof REFERENTIELS_ETAT, Referentiel> | null>(null);
  protected readonly erreur = signal(false);
  protected readonly enCours = computed(() => (this.etat()?.incidents ?? []).filter((i) => !i.fin));
  protected readonly passes = computed(() => (this.etat()?.incidents ?? []).filter((i) => !!i.fin));

  constructor() {
    effect(() => this.charger(this.langues.langue()));
  }

  private charger(langue: string): void {
    forkJoin({
      etat: this.http.get<VueEtat>(`${this.configuration.apiUrl}/api/v1/availability`),
      impacts: this.referentiels.referentiel(REFERENTIELS_ETAT.impacts, langue),
      composants: this.referentiels.referentiel(REFERENTIELS_ETAT.composants, langue),
      phases: this.referentiels.referentiel(REFERENTIELS_ETAT.phases, langue),
      objectifs: this.referentiels.referentiel(REFERENTIELS_ETAT.objectifs, langue),
    }).subscribe({
      next: ({ etat, ...refs }) => { this.etat.set(etat); this.refs.set(refs); this.erreur.set(false); },
      error: () => this.erreur.set(true),
    });
  }

  protected libelle(ref: keyof typeof REFERENTIELS_ETAT, code: string): string {
    return this.refs()?.[ref].valeurs.find((v) => v.code === code)?.libelle ?? code;
  }

  /** 0 : état normal (premier impact du référentiel) ; plus grand : plus grave. */
  protected gravite(code: string): number {
    return Math.max(0, this.refs()?.impacts.valeurs.findIndex((v) => v.code === code) ?? 0);
  }

  protected objectifs(): readonly ValeurReferentiel[] {
    return this.refs()?.objectifs.valeurs ?? [];
  }

  protected texte(textes: Record<string, string>): string {
    return textes[this.langues.langue()] ?? Object.values(textes)[0] ?? '';
  }

  /** Date dans la langue choisie, sans données de paramètres régionaux à charger. */
  protected date(iso: string | null, style: 'medium' | 'short'): string {
    return iso ? new Intl.DateTimeFormat(this.langues.langue(), { dateStyle: style, timeStyle: 'short' }).format(new Date(iso)) : '';
  }

  protected composants(): string[] {
    return Object.keys(this.etat()?.composants ?? {});
  }
}
