/** Contrats de l'API du signataire (VS-040 à VS-052), tels que le serveur les rend. */
export interface VueSession {
  jetonSession: string;
  expireLe: string;
  enveloppe: string;
  mode: string;
  statutEnveloppe: string;
  nom: string;
  langue: string;
  peutAgir: boolean;
  aConsenti: boolean;
  versionConsentement: string;
  texteConsentement: string;
  actions: string[];
  /** Origines déclarées par l'application d'une session intégrée ; vide pour un lien d'invitation. */
  originesIntegration: string[];
  /** Niveau d'authentification exigé avant de signer, ou null. */
  niveauExige: string | null;
  /** Niveau déjà atteint (attestation de l'application), ou null. */
  niveauAtteint: string | null;
  /** Marque de l'organisation (VS-123), ou absente. */
  marque?: Marque | null;
}

/** Marque d'une organisation : la couleur garantit 4,5:1 avec le blanc (contrôlée par le serveur) ; logo en URL de données PNG. */
export interface Marque {
  nomAffiche: string;
  couleurPrimaire: string;
  logo?: string | null;
}

export interface Champ {
  id: string;
  signataire: string;
  type: string;
  libelle: string;
  page: number;
  x: number;
  y: number;
  largeur: number;
  hauteur: number;
  obligatoire: boolean;
}

/** Problème RFC 9457 rendu par l'API. */
export interface Probleme {
  type?: string;
  detail?: string;
  status?: number;
}

/** Dernier segment du type d'un problème (ex. « lien-consomme »), ou « inconnu ». */
export function typeProbleme(erreur: unknown): string {
  const corps = (erreur as { error?: Probleme })?.error;
  return corps?.type?.split('/').pop() ?? 'inconnu';
}

/** Options d'une cérémonie de clé d'accès, valeurs binaires en base64url. */
export interface OptionsCleAcces {
  defi: string;
  rpId: string;
  identifiantUtilisateur: string;
  nomAffiche: string;
  clesPermises: string[];
  delaiMs: number;
}

/** Vérification d'identité (VS-131) : démarrée chez le fournisseur, puis suivie jusqu'à son rappel. */
export interface DemarrageVerification {
  verification: string;
  url: string;
}

export interface SuiviVerification {
  id: string;
  etat: 'en-cours' | 'reussie' | 'echouee';
  methode: string | null;
}

export interface VerificationCode {
  valide: boolean;
  essaisRestants: number;
}
