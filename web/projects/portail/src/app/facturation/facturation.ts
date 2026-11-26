import { HttpClient } from '@angular/common/http';
import { Component, InjectionToken, effect, inject, signal } from '@angular/core';
import { CONFIGURATION, LangueService, ListeReferentiel, Referentiel, ReferentielService, TraductionService } from '@vinasign/commun';
import { forkJoin } from 'rxjs';

/** Forme rendue par GET /api/v1/billing (VS-121). */
export interface VueFacturation {
  plan: string | null;
  etat: string | null;
  factures: VueFacture[];
}

export interface VueFacture {
  numero: string;
  montant: number;
  devise: string;
  url: string | null;
  pdf: string | null;
  debut: string | null;
  fin: string | null;
  payeeLe: string | null;
}

/** Navigation vers la page hébergée du prestataire ; remplacée en test. */
export const OUVRIR_PAGE = new InjectionToken<(url: string) => void>('OUVRIR_PAGE', { providedIn: 'root', factory: () => (url: string) => window.location.assign(url) });

/** Référentiels qui donnent leurs libellés aux codes de l'écran. */
export const REFERENTIELS_FACTURATION = { plans: 'plan', etats: 'etat-abonnement' };

/**
 * Écran « Facturation » (VS-121, VS-ADR-009) : plan payé et état de l'abonnement, factures, choix d'un plan payé chez le
 * prestataire, gestion de l'abonnement (moyen de paiement, résiliation) sur sa page hébergée. Aucune donnée de carte ne
 * transite par VinaSign.
 */
@Component({
  selector: 'app-facturation',
  imports: [ListeReferentiel],
  templateUrl: './facturation.html',
  styleUrl: './facturation.css',
})
export class Facturation {
  private readonly http = inject(HttpClient);
  private readonly api = inject(CONFIGURATION).apiUrl + '/api/v1/billing';
  private readonly referentiels = inject(ReferentielService);
  private readonly ouvrir = inject(OUVRIR_PAGE);
  protected readonly langues = inject(LangueService);
  protected readonly i18n = inject(TraductionService);
  protected readonly situation = signal<VueFacturation | null>(null);
  protected readonly refs = signal<Record<keyof typeof REFERENTIELS_FACTURATION, Referentiel> | null>(null);
  protected readonly erreur = signal('');
  protected readonly envoi = signal(false);
  protected readonly plan = signal('');

  constructor() {
    effect(() => this.charger(this.langues.langue()));
  }

  private charger(langue: string): void {
    forkJoin({
      situation: this.http.get<VueFacturation>(this.api),
      plans: this.referentiels.referentiel(REFERENTIELS_FACTURATION.plans, langue),
      etats: this.referentiels.referentiel(REFERENTIELS_FACTURATION.etats, langue),
    }).subscribe({
      next: ({ situation, ...refs }) => { this.situation.set(situation); this.refs.set(refs); this.erreur.set(''); },
      error: () => this.erreur.set('facturation.erreur'),
    });
  }

  protected libelle(ref: keyof typeof REFERENTIELS_FACTURATION, code: string | null): string {
    return code === null ? '' : this.refs()?.[ref].valeurs.find((v) => v.code === code)?.libelle ?? code;
  }

  protected payer(): void {
    this.rediriger('/checkout', { plan: this.plan(), langue: this.langues.langue() });
  }

  protected gerer(): void {
    this.rediriger('/portal', {});
  }

  private rediriger(chemin: string, corps: object): void {
    this.envoi.set(true);
    this.http.post<{ url: string }>(this.api + chemin, corps).subscribe({
      next: (r) => this.ouvrir(r.url),
      error: () => { this.envoi.set(false); this.erreur.set('facturation.erreurPrestataire'); },
    });
  }

  /** Montant en plus petite unité de la devise (cents), affiché dans la langue choisie. */
  protected montant(f: VueFacture): string {
    return new Intl.NumberFormat(this.langues.langue(), { style: 'currency', currency: f.devise.toUpperCase() }).format(f.montant / 100);
  }

  protected date(iso: string | null): string {
    return iso ? new Intl.DateTimeFormat(this.langues.langue(), { dateStyle: 'medium' }).format(new Date(iso)) : '';
  }
}
