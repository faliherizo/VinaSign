package ca.vinaitsolution.vinasign.facturation.domain;

import java.time.Instant;

/** Événement du prestataire, authentifié et traduit (VS-121) ; {@code id} sert à ne le traiter qu'une fois. */
public sealed interface EvenementPaiement {

    String id();

    /** Paiement initial réussi : l'organisation (référence fournie au départ) devient cliente avec ce plan. */
    record PaiementConfirme(String id, java.util.UUID organisation, String client, String reference, String plan) implements EvenementPaiement {
    }

    /** Abonnement modifié (plan, état) ou terminé chez le prestataire. */
    record AbonnementModifie(String id, String client, String reference, String plan, EtatAbonnement etat) implements EvenementPaiement {
    }

    /** Facture payée. */
    record FacturePayee(String id, String client, String reference, String numero, long montant, String devise, String urlHebergee, String urlPdf,
                        Instant debutPeriode, Instant finPeriode, Instant payeeLe) implements EvenementPaiement {
    }

    /** Événement sans effet pour VinaSign (type non suivi) : accusé, ignoré. */
    record Ignore(String id) implements EvenementPaiement {
    }
}
