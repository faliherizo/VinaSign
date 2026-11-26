import { Component, computed, HostListener, inject, input, OnInit, signal } from '@angular/core';
import { Location } from '@angular/common';
import { forkJoin } from 'rxjs';
import { LangueService, ListeReferentiel, TraductionService } from '@vinasign/commun';
import { Champ, typeProbleme, VueSession } from '../api/modeles';
import { SignataireApi } from '../api/signataire-api';
import { ChampsSaisie } from '../champs/champs-saisie';
import { PadSignature, SignatureProduite } from '../signature/pad-signature';
import { VisionneusePdf } from '../visionneuse/visionneuse-pdf';
import { Avis, AvisParent } from './avis-parent';
import { TypesChamp } from '../champs/types-champ';
import { EtapeAuthentification } from '../authentification/etape-authentification';

export type Etape = 'ouverture' | 'erreur' | 'consentement' | 'authentification' | 'attente' | 'signature' | 'refus' | 'signe' | 'refuse';

/** Étapes annoncées au signataire ; chaque étape du parcours y correspond, sauf l'ouverture, l'attente et l'erreur. */
type EtapeAnnoncee = 'consentement' | 'authentification' | 'signature' | 'fin';
const ANNONCE: Partial<Record<Etape, EtapeAnnoncee>> = {
  consentement: 'consentement', authentification: 'authentification', signature: 'signature', refus: 'signature', signe: 'fin', refuse: 'fin',
};

/** Référentiel des motifs de refus (VS-045). */
export const REFERENTIEL_MOTIFS = 'motif-refus';

/**
 * Parcours du signataire (VS-040 à VS-052, VS-045) : le jeton du lien est échangé contre une session puis retiré de la
 * barre d'adresse ; consentement, lecture, champs, signature ou refus. Une action non permise par la session intégrée
 * n'est pas proposée (le serveur la refuse de toute façon).
 */
@Component({
  selector: 'vs-parcours',
  imports: [VisionneusePdf, ChampsSaisie, PadSignature, ListeReferentiel, EtapeAuthentification],
  templateUrl: './parcours.html',
  styleUrl: './parcours.css',
  host: { '[style.--marque]': 'marque()?.couleurPrimaire ?? null' },
})
export class Parcours implements OnInit {
  protected readonly motifs = REFERENTIEL_MOTIFS;
  protected readonly i18n = inject(TraductionService);
  private readonly api = inject(SignataireApi);
  private readonly location = inject(Location);
  private readonly avis = inject(AvisParent);
  private readonly types = inject(TypesChamp);
  private readonly langues = inject(LangueService);

  /** Jeton du lien, lu dans le chemin (liaison des paramètres de route). */
  readonly jeton = input.required<string>();

  protected readonly etape = signal<Etape>('ouverture');
  protected readonly erreur = signal('');
  protected readonly session = signal<VueSession | null>(null);
  protected readonly document = signal<ArrayBuffer | null>(null);
  protected readonly champs = signal<readonly Champ[]>([]);
  protected readonly valeurs = signal<Readonly<Record<string, string>>>({});
  protected readonly manquants = signal<readonly string[]>([]);
  protected readonly signature = signal<SignatureProduite | null>(null);
  protected readonly accepte = signal(false);
  protected readonly envoi = signal(false);
  protected readonly motif = signal('');
  protected readonly messageEnvoi = signal('');

  /** Marque de l'organisation (VS-123) : sa couleur remplace celle des actions principales, son logo et son nom s'affichent. */
  protected readonly marque = computed(() => this.session()?.marque ?? null);
  protected readonly peutSigner = computed(() => this.permis('signer'));
  /** Étapes du parcours, la vérification d'identité n'y figurant que si elle est exigée. */
  protected readonly progression = computed<readonly EtapeAnnoncee[]>(() =>
    this.session()?.niveauExige ? ['consentement', 'authentification', 'signature', 'fin'] : ['consentement', 'signature', 'fin']);
  /** Rang de l'étape courante dans la progression ; -1 quand aucune n'est annoncée. */
  protected readonly rangCourant = computed(() => {
    const annoncee = ANNONCE[this.etape()];
    return annoncee ? this.progression().indexOf(annoncee) : -1;
  });
  protected readonly peutRefuser = computed(() => this.permis('refuser'));

  ngOnInit(): void {
    const jeton = this.jeton();
    this.location.replaceState(this.location.path().replace(jeton, '').replace(/\/+$/, '') || '/');
    this.api.ouvrir(jeton).subscribe({
      next: (s) => {
        this.session.set(s);
        this.langues.choisir(s.langue);
        this.avis.autoriser(s.originesIntegration);
        this.avis.signaler('ready');
        this.suivante(s);
      },
      error: (e) => this.echouer(e),
    });
  }

  /** La page intégrée se ferme (navigation, onglet fermé) : l'application en est avisée (VS-055). */
  @HostListener('window:pagehide')
  protected fermer(): void {
    this.avis.signaler('closed');
  }

  protected consentir(): void {
    const s = this.session()!;
    this.envoi.set(true);
    this.api.consentir(s.versionConsentement).subscribe({
      next: () => {
        this.envoi.set(false);
        this.session.set({ ...s, aConsenti: true });
        this.suivante({ ...s, aConsenti: true });
      },
      error: (e) => this.echouer(e),
    });
  }

  protected signer(): void {
    const manquants = this.champsManquants();
    this.manquants.set(manquants);
    if (manquants.length > 0 || !this.signature()) {
      this.messageEnvoi.set(this.i18n.t(manquants.length > 0 ? 'signature.champsManquants' : 'signature.absente'));
      return;
    }
    this.envoi.set(true);
    this.api.signer({ ...this.signature()!, valeurs: this.valeursAEnvoyer() }).subscribe({
      next: () => this.terminer('signe', 'signed'),
      error: (e) => this.echouer(e),
    });
  }

  /** Identité vérifiée (code ou clé d'accès) : le niveau exigé est atteint pour cette session. */
  protected authentifie(): void {
    const s = this.session()!;
    this.session.set({ ...s, niveauAtteint: s.niveauExige });
    this.charger();
  }

  protected demanderRefus(): void {
    this.etape.set('refus');
  }

  protected annulerRefus(): void {
    this.etape.set('signature');
  }

  protected refuser(): void {
    this.envoi.set(true);
    this.api.refuser(this.motif() || null).subscribe({
      next: () => this.terminer('refuse', 'declined'),
      error: (e) => this.echouer(e),
    });
  }

  private permis(action: string): boolean {
    const actions = this.session()?.actions ?? [];
    return actions.length === 0 || actions.includes(action);
  }

  private suivante(s: VueSession): void {
    if (!s.peutAgir) {
      this.etape.set('attente');
    } else if (!s.aConsenti) {
      this.etape.set('consentement');
    } else if (s.niveauExige && !s.niveauAtteint) {
      this.etape.set('authentification');
    } else {
      this.charger();
    }
  }

  private charger(): void {
    forkJoin({ document: this.api.document(), champs: this.api.champs() }).subscribe({
      next: ({ document, champs }) => {
        this.document.set(document);
        this.champs.set(champs);
        this.etape.set('signature');
      },
      error: (e) => this.echouer(e),
    });
  }

  /** Champs obligatoires restés vides, hors champs remplis par l'acte de signer. */
  private champsManquants(): string[] {
    const v = this.valeurs();
    return this.champs().filter((c) => c.obligatoire && !this.types.rempliParSignature(c) && !(v[c.id] ?? '').trim()).map((c) => c.id);
  }

  private valeursAEnvoyer(): Record<string, string> {
    const mode = this.signature()?.image ? 'dessin' : 'saisie';
    const valeurs: Record<string, string> = {};
    for (const [id, valeur] of Object.entries(this.valeurs())) {
      if (valeur.trim()) {
        valeurs[id] = valeur;
      }
    }
    for (const champ of this.champs().filter((c) => this.types.rempliParSignature(c))) {
      valeurs[champ.id] = mode;
    }
    return valeurs;
  }

  private terminer(etape: 'signe' | 'refuse', avis: Avis): void {
    this.envoi.set(false);
    this.etape.set(etape);
    this.avis.signaler(avis);
  }

  private echouer(e: unknown): void {
    this.envoi.set(false);
    const type = typeProbleme(e);
    this.erreur.set(this.i18n.t(`erreur.${type}`) === `erreur.${type}` ? this.i18n.t('erreur.inconnu') : this.i18n.t(`erreur.${type}`));
    this.etape.set('erreur');
    this.avis.signaler('error');
  }
}
