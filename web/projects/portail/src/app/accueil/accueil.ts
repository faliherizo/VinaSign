import { Component, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { TraductionService } from '@vinasign/commun';
import { SessionPortail } from '../connexion/session-portail';

/** Étapes et atouts présentés : clés de traduction, dans l'ordre d'affichage. */
const ETAPES = ['preparer', 'placer', 'signer', 'prouver'] as const;
const ATOUTS = ['manuscrite', 'documents', 'ordre', 'cachet', 'preuve', 'integration', 'isolation', 'accessibilite'] as const;
const DEMO = 'medias/demo/vinasign-demo';

/**
 * Vitrine du portail : ce que fait VinaSign, la démonstration filmée (tournée à partir du test de bout en bout, sous-titrée,
 * transcription en texte), comment ça marche, et l'accès au portail selon que la personne est connectée ou non.
 */
@Component({
  selector: 'app-accueil',
  imports: [RouterLink],
  templateUrl: './accueil.html',
  styleUrl: './accueil.css',
})
export class Accueil {
  protected readonly i18n = inject(TraductionService);
  protected readonly session = inject(SessionPortail);
  protected readonly etapes = ETAPES;
  protected readonly atouts = ATOUTS;
  protected readonly video = `${DEMO}.mp4`;
  protected readonly sousTitres = `${DEMO}.vtt`;
  protected readonly affiche = `${DEMO}.jpg`;
  /** Vidéo introuvable (non publiée sur ce serveur) : un message remplace le lecteur. */
  protected readonly videoIndisponible = signal(false);

  protected connecter(): void {
    void this.session.connecter('/enveloppes');
  }
}
