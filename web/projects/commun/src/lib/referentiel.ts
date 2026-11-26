/** Formes rendues par GET /api/v1/referentiels et GET /api/v1/referentiels/{code}. */
export interface ResumeReferentiel {
  readonly code: string;
  readonly libelle: string;
  readonly nombreValeurs: number;
}

export interface ValeurReferentiel {
  readonly code: string;
  readonly libelle: string;
  readonly ordre: number;
  readonly attributs: Readonly<Record<string, string>>;
}

export interface Referentiel {
  readonly code: string;
  readonly libelle: string;
  readonly langue: string;
  readonly valeurs: readonly ValeurReferentiel[];
}
