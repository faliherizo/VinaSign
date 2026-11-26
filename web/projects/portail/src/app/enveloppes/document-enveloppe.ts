import { Component, OnDestroy, OnInit, computed, inject, input, signal } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { LangueService, ReferentielService, TraductionService, ValeurReferentiel } from '@vinasign/commun';
import { concatMap, forkJoin } from 'rxjs';
import { SessionPortail } from '../connexion/session-portail';
import { DetailEnveloppe, EnveloppesService } from './enveloppes.service';
import { PlacementZones } from './placement-zones';
import { Zone } from './zones';

/**
 * Document d'une enveloppe. En brouillon, le préparateur y place les zones de signature puis envoie (VS-060) ; ensuite,
 * chacun y relit le document, sa version finale scellée dès qu'elle existe. Un Word déposé s'y voit converti en PDF.
 */
@Component({
  selector: 'app-document-enveloppe',
  imports: [PlacementZones, RouterLink],
  templateUrl: './document-enveloppe.html',
  styleUrl: './enveloppes.css',
})
export class DocumentEnveloppe implements OnInit, OnDestroy {
  private readonly enveloppes = inject(EnveloppesService);
  private readonly referentiels = inject(ReferentielService);
  private readonly langues = inject(LangueService);
  private readonly session = inject(SessionPortail);
  private readonly router = inject(Router);
  protected readonly i18n = inject(TraductionService);

  /** Identifiant lu dans l'adresse. */
  readonly id = input.required<string>();

  protected readonly enveloppe = signal<DetailEnveloppe | null>(null);
  protected readonly donnees = signal<ArrayBuffer | null>(null);
  protected readonly lien = signal('');
  protected readonly types = signal<readonly ValeurReferentiel[]>([]);
  protected readonly zones = signal<readonly Zone[]>([]);
  protected readonly erreur = signal('');
  protected readonly message = signal('');
  protected readonly occupe = signal(false);
  protected readonly nature = computed(() => {
    const natures = this.enveloppe()?.documents.map((d) => d.nature) ?? [];
    return ['final', 'original'].find((n) => natures.includes(n)) ?? null;
  });
  protected readonly modifiable = computed(() => this.enveloppe()?.statut === 'brouillon' && this.session.aLeRole('preparateur'));

  ngOnInit(): void {
    forkJoin({ enveloppe: this.enveloppes.consulter(this.id()), zones: this.enveloppes.champs(this.id()),
      types: this.referentiels.referentiel('type-champ', this.langues.langue()) }).subscribe({
      next: (r) => {
        this.enveloppe.set(r.enveloppe);
        this.zones.set(r.zones);
        this.types.set(r.types.valeurs);
        this.chargerDocument();
      },
      error: () => this.erreur.set('document.erreurEnveloppe'),
    });
  }

  ngOnDestroy(): void {
    URL.revokeObjectURL(this.lien());
  }

  protected enregistrer(): void {
    this.agir(this.enveloppes.definirChamps(this.id(), this.zones()), () => this.message.set('placement.enregistre'));
  }

  protected envoyer(): void {
    this.agir(this.enveloppes.definirChamps(this.id(), this.zones()).pipe(concatMap(() => this.enveloppes.envoyer(this.id()))),
      () => void this.router.navigate(['/enveloppes'], { queryParams: { envoyee: this.id() } }));
  }

  private agir(appel: ReturnType<EnveloppesService['envoyer']> | ReturnType<EnveloppesService['definirChamps']>, suite: () => void): void {
    this.occupe.set(true);
    this.message.set('');
    this.erreur.set('');
    (appel as ReturnType<EnveloppesService['envoyer']>).subscribe({
      next: () => { this.occupe.set(false); suite(); },
      error: () => { this.occupe.set(false); this.erreur.set('placement.echec'); },
    });
  }

  private chargerDocument(): void {
    const nature = this.nature();
    if (nature === null) {
      return;
    }
    this.enveloppes.document(this.id(), nature).subscribe({
      next: (octets) => {
        this.donnees.set(octets);
        // PDF.js transfère le tampon à son exécuteur : le téléchargement garde sa propre copie.
        this.lien.set(URL.createObjectURL(new Blob([octets.slice(0)], { type: 'application/pdf' })));
      },
      error: () => this.erreur.set('document.erreur'),
    });
  }
}
