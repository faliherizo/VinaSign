import { Component, effect, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { LangueService, ListeReferentiel, Referentiel, ReferentielService, TraductionService } from '@vinasign/commun';
import { EnveloppesService, ResumeEnveloppe } from './enveloppes.service';
import { SessionPortail } from '../connexion/session-portail';

/** Taille d'une page de la liste ; le serveur borne à 100. */
export const TAILLE_PAGE = 25;

/**
 * Enveloppes du portail de l'organisation (VS-064), les plus récentes d'abord, filtrables par statut (référentiel
 * statut-enveloppe) ; « Afficher plus » charge la page suivante. Le préparateur y trouve le lien d'une nouvelle enveloppe.
 */
@Component({
  selector: 'app-liste-enveloppes',
  imports: [ListeReferentiel, RouterLink],
  templateUrl: './liste-enveloppes.html',
  styleUrl: './enveloppes.css',
})
export class ListeEnveloppes {
  private readonly enveloppes = inject(EnveloppesService);
  private readonly referentiels = inject(ReferentielService);
  protected readonly langues = inject(LangueService);
  protected readonly i18n = inject(TraductionService);
  protected readonly session = inject(SessionPortail);
  protected readonly statut = signal('');
  protected readonly elements = signal<ResumeEnveloppe[]>([]);
  protected readonly suivant = signal<string | null>(null);
  protected readonly statuts = signal<Referentiel | null>(null);
  protected readonly erreur = signal(false);
  protected readonly chargement = signal(false);

  constructor() {
    effect(() => this.charger(this.statut(), null));
    effect(() => this.referentiels.referentiel('statut-enveloppe', this.langues.langue()).subscribe({ next: (r) => this.statuts.set(r), error: () => undefined }));
  }

  protected plus(): void {
    this.charger(this.statut(), this.suivant());
  }

  private charger(statut: string, apres: string | null): void {
    this.chargement.set(true);
    this.enveloppes.lister(statut, apres, TAILLE_PAGE).subscribe({
      next: (page) => {
        this.elements.set(apres === null ? page.elements : [...this.elements(), ...page.elements]);
        this.suivant.set(page.suivant);
        this.erreur.set(false);
        this.chargement.set(false);
      },
      error: () => { this.erreur.set(true); this.chargement.set(false); },
    });
  }

  protected libelleStatut(code: string): string {
    return this.statuts()?.valeurs.find((v) => v.code === code)?.libelle ?? code;
  }

  protected date(iso: string): string {
    return new Intl.DateTimeFormat(this.langues.langue(), { dateStyle: 'medium' }).format(new Date(iso));
  }
}
