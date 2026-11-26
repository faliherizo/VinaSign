import { Component, InjectionToken, inject, signal } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { LangueService, ListeReferentiel, TraductionService } from '@vinasign/commun';
import { Observable, concatMap, map, of, tap } from 'rxjs';
import { EnveloppesService, NouveauSignataire } from './enveloppes.service';

/** Clé d'idempotence de la création : un double clic ne crée jamais deux enveloppes. Remplacée en test. */
export const CLE_IDEMPOTENCE = new InjectionToken<() => string>('CLE_IDEMPOTENCE', { providedIn: 'root', factory: () => () => crypto.randomUUID() });

/** Saisie d'un signataire ; langue et type sont des codes de référentiel. */
interface SaisieSignataire {
  nom: string;
  courriel: string;
  langue: string;
  type: string;
}

/** Étapes de l'envoi, annoncées à mesure ; la dernière atteinte dit où reprendre en cas d'échec. */
export type EtapeEnvoi = 'creation' | 'depot' | 'signataires' | 'envoi';

/**
 * Préparation et envoi d'une enveloppe depuis le portail (VS-064, VS-ADR-009), sur téléphone comme sur ordinateur :
 * référence, mode, échéance, document (PDF, ou Word converti), signataires, puis placement des zones ou envoi. Le serveur valide tout ; l'écran dit quelle étape a
 * échoué et garde la saisie.
 */
@Component({
  selector: 'app-nouvelle-enveloppe',
  imports: [ListeReferentiel, RouterLink],
  templateUrl: './nouvelle-enveloppe.html',
  styleUrl: './enveloppes.css',
})
export class NouvelleEnveloppe {
  private readonly enveloppes = inject(EnveloppesService);
  private readonly router = inject(Router);
  private readonly cle = inject(CLE_IDEMPOTENCE);
  protected readonly langues = inject(LangueService);
  protected readonly i18n = inject(TraductionService);
  protected readonly reference = signal('');
  protected readonly mode = signal('');
  protected readonly echeance = signal('');
  protected readonly fichier = signal<File | null>(null);
  protected readonly dansLOrdre = signal(true);
  protected readonly signataires = signal<SaisieSignataire[]>([this.vide()]);
  protected readonly etape = signal<EtapeEnvoi | null>(null);
  protected readonly echec = signal<EtapeEnvoi | null>(null);
  protected readonly envoi = signal(false);
  /** Brouillon déjà créé par un essai précédent : une reprise le poursuit au lieu de heurter sa référence unique. */
  protected readonly brouillon = signal<{ id: string; depose: boolean } | null>(null);
  /** Suite demandée : placer les zones sur le document, ou envoyer aussitôt (zones posées par défaut). */
  private suite: 'placement' | 'envoi' = 'placement';

  protected texte(evenement: Event): string {
    return (evenement.target as HTMLInputElement).value;
  }

  protected choisirFichier(evenement: Event): void {
    this.fichier.set((evenement.target as HTMLInputElement).files?.item(0) ?? null);
  }

  protected modifier(rang: number, champ: keyof SaisieSignataire, valeur: string): void {
    this.signataires.update((liste) => liste.map((s, i) => (i === rang ? { ...s, [champ]: valeur } : s)));
  }

  protected ajouter(): void {
    this.signataires.update((liste) => [...liste, this.vide()]);
  }

  protected retirer(rang: number): void {
    this.signataires.update((liste) => liste.filter((_, i) => i !== rang));
  }

  protected choisirSuite(suite: 'placement' | 'envoi'): void {
    this.suite = suite;
  }

  protected envoyer(): void {
    const fichier = this.fichier();
    if (fichier === null || this.envoi()) {
      return;
    }
    const suite = this.suite;
    this.envoi.set(true);
    this.echec.set(null);
    this.etapes(fichier, suite).subscribe({
      next: (id) => void (suite === 'envoi' ? this.router.navigate(['/enveloppes'], { queryParams: { envoyee: id } })
        : this.router.navigate(['/enveloppes', id])),
      error: () => { this.echec.set(this.etape()); this.envoi.set(false); },
    });
  }

  private etapes(fichier: File, suite: 'placement' | 'envoi'): Observable<string> {
    return this.creer().pipe(
      concatMap((id) => this.deposer(id, fichier)),
      concatMap((id) => this.suivre('signataires', () => this.enveloppes.definirSignataires(id, this.saisis()), id)),
      concatMap((id) => (suite === 'envoi' ? this.suivre('envoi', () => this.enveloppes.envoyer(id), id) : of(id))),
    );
  }

  private creer(): Observable<string> {
    const b = this.brouillon();
    if (b !== null) {
      return of(b.id);
    }
    this.etape.set('creation');
    const expiresAt = new Date(this.echeance() + 'T23:59:59').toISOString();
    return this.enveloppes.creer({ externalReference: this.reference().trim(), mode: this.mode(), expiresAt }, this.cle()).pipe(
      map((e) => e.id), tap((id) => this.brouillon.set({ id, depose: false })));
  }

  private deposer(id: string, fichier: File): Observable<string> {
    if (this.brouillon()?.depose) {
      return of(id);
    }
    this.etape.set('depot');
    return this.enveloppes.demanderDepot(id).pipe(
      concatMap((d) => this.enveloppes.deposer(d.url, fichier).pipe(map(() => d.id))),
      concatMap((depot) => this.enveloppes.confirmerDepot(id, depot)),
      map(() => id), tap(() => this.brouillon.set({ id, depose: true })),
    );
  }

  private suivre(etape: EtapeEnvoi, appel: () => Observable<unknown>, id: string): Observable<string> {
    this.etape.set(etape);
    return appel().pipe(map(() => id));
  }

  private saisis(): NouveauSignataire[] {
    return this.signataires().map((s, i) => ({ nom: s.nom.trim(), courriel: s.courriel.trim(), langue: s.langue, type: s.type,
      ordre: this.dansLOrdre() ? i + 1 : 1 }));
  }

  private vide(): SaisieSignataire {
    return { nom: '', courriel: '', langue: this.langues.langue(), type: '' };
  }
}
