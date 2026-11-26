import { Component, effect, inject, input, OnDestroy, output, signal } from '@angular/core';
import { LangueService, ListeReferentiel, ReferentielService, TraductionService } from '@vinasign/commun';
import { firstValueFrom } from 'rxjs';
import { typeProbleme } from '../api/modeles';
import { SignataireApi } from '../api/signataire-api';
import { ClesNavigateur } from './cles-navigateur';

/** Référentiel des canaux de code (courriel, SMS). */
export const REFERENTIEL_CANAUX = 'canal-code';
/** Référentiel des niveaux : l'attribut « methode » d'un niveau dit s'il exige une vérification d'identité (VS-131). */
export const REFERENTIEL_NIVEAUX = 'niveau-authentification';
/** Suivi de la vérification chez le fournisseur : intervalle et nombre d'interrogations (dix minutes). */
export const SUIVI_IDENTITE = { intervalleMs: 3000, essais: 200 };

/**
 * Vérification d'identité avant de signer (VS-042, VS-043) : clé d'accès si l'appareil en a une, sinon code reçu par le
 * canal choisi. Après un code, le signataire peut enregistrer une clé d'accès pour la prochaine fois. Quand le niveau exigé
 * demande une identité vérifiée (VS-131), le signataire la fait vérifier chez le fournisseur, dans un nouvel onglet, et
 * l'écran suit le résultat.
 */
@Component({
  selector: 'vs-etape-authentification',
  imports: [ListeReferentiel],
  templateUrl: './etape-authentification.html',
  styleUrl: './etape-authentification.css',
})
export class EtapeAuthentification implements OnDestroy {
  protected readonly i18n = inject(TraductionService);
  private readonly api = inject(SignataireApi);
  protected readonly cles = inject(ClesNavigateur);

  private readonly referentiels = inject(ReferentielService);
  private readonly langues = inject(LangueService);

  /** Niveau exigé du signataire (code du référentiel), ou null. */
  readonly niveau = input<string | null>(null);
  readonly authentifie = output<void>();

  protected readonly exigeIdentite = signal(false);
  protected readonly enVerification = signal(false);
  private suivi: ReturnType<typeof setTimeout> | undefined;

  constructor() {
    effect(() => {
      const niveau = this.niveau();
      if (!niveau) return;
      this.referentiels.referentiel(REFERENTIEL_NIVEAUX, this.langues.langue()).subscribe({
        next: (r) => this.exigeIdentite.set(r.valeurs.find((v) => v.code === niveau)?.attributs['methode'] === 'verification-identite'),
        error: () => this.exigeIdentite.set(false),
      });
    });
  }

  ngOnDestroy(): void {
    clearTimeout(this.suivi);
  }

  /** Le fournisseur s'ouvre dans un nouvel onglet ; l'écran interroge l'état jusqu'au rappel du fournisseur. */
  protected async verifierIdentite(): Promise<void> {
    await this.essayer(async () => {
      const d = await firstValueFrom(this.api.demarrerVerificationIdentite());
      window.open(d.url, '_blank', 'noopener');
      this.enVerification.set(true);
      this.message.set(this.i18n.t('authentification.identiteEnCours'));
      this.suivre(d.verification, SUIVI_IDENTITE.essais);
    });
  }

  private suivre(id: string, restants: number): void {
    this.suivi = setTimeout(() => {
      this.api.suivreVerificationIdentite(id).subscribe({
        next: (s) => {
          if (s.etat === 'reussie') {
            this.enVerification.set(false);
            this.authentifie.emit();
          } else if (s.etat === 'echouee' || restants <= 1) {
            this.enVerification.set(false);
            this.message.set(this.i18n.t(s.etat === 'echouee' ? 'authentification.identiteEchouee' : 'authentification.identiteDelai'));
          } else {
            this.suivre(id, restants - 1);
          }
        },
        error: () => {
          this.enVerification.set(false);
          this.message.set(this.i18n.t('authentification.echec'));
        },
      });
    }, SUIVI_IDENTITE.intervalleMs);
  }

  protected readonly canaux = REFERENTIEL_CANAUX;
  protected readonly canal = signal('');
  protected readonly code = signal('');
  protected readonly codeEnvoye = signal(false);
  protected readonly proposerCle = signal(false);
  protected readonly message = signal('');
  protected readonly occupe = signal(false);

  protected async avecCle(): Promise<void> {
    await this.essayer(async () => {
      const options = await firstValueFrom(this.api.optionsCleAcces('authentification'));
      await firstValueFrom(this.api.authentifierCleAcces(await this.cles.utiliser(options)));
      this.authentifie.emit();
    });
  }

  protected async envoyerCode(): Promise<void> {
    await this.essayer(async () => {
      await firstValueFrom(this.api.demanderCode(this.canal()));
      this.codeEnvoye.set(true);
      this.message.set(this.i18n.t('authentification.codeEnvoye'));
    });
  }

  protected async verifierCode(): Promise<void> {
    await this.essayer(async () => {
      const r = await firstValueFrom(this.api.verifierCode(this.code().trim()));
      if (!r.valide) {
        this.message.set(`${this.i18n.t('authentification.codeFaux')} ${r.essaisRestants}`);
        return;
      }
      if (this.cles.disponible()) {
        this.proposerCle.set(true);
      } else {
        this.authentifie.emit();
      }
    });
  }

  protected async enregistrerCle(): Promise<void> {
    await this.essayer(async () => {
      const options = await firstValueFrom(this.api.optionsCleAcces('enregistrement'));
      await firstValueFrom(this.api.enregistrerCleAcces(await this.cles.creer(options)));
      this.authentifie.emit();
    });
  }

  protected continuer(): void {
    this.authentifie.emit();
  }

  /** Un refus de l'API (ou de l'appareil) devient un message traduit ; le code reste toujours possible. */
  private async essayer(action: () => Promise<void>): Promise<void> {
    this.occupe.set(true);
    this.message.set('');
    try {
      await action();
    } catch (e) {
      const type = typeProbleme(e);
      const cle = `erreur.${type}`;
      this.message.set(this.i18n.t(cle) === cle ? this.i18n.t('authentification.echec') : this.i18n.t(cle));
    } finally {
      this.occupe.set(false);
    }
  }
}
