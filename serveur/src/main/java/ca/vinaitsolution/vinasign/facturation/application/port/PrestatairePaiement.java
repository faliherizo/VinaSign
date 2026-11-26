package ca.vinaitsolution.vinasign.facturation.application.port;

import ca.vinaitsolution.vinasign.facturation.domain.EvenementPaiement;
import java.util.UUID;

/**
 * Prestataire de paiement (VS-121) : page de paiement et portail de gestion hébergés chez lui ; ses événements, signés,
 * annoncent paiements, changements d'abonnement et factures. Aucun moyen de paiement ne transite par VinaSign.
 */
public interface PrestatairePaiement {

    /** @param client client déjà connu du prestataire, ou null pour un premier paiement */
    record Souscription(UUID organisation, String plan, String client, String langue) {
    }

    String code();

    /** Adresse de la page de paiement hébergée ; {@code PaiementIndisponible} si le plan n'a pas de prix chez le prestataire. */
    String ouvrirPaiement(Souscription souscription);

    /** Adresse du portail hébergé (moyen de paiement, factures, résiliation). */
    String ouvrirGestion(String client);

    /** Événement authentifié et traduit ; {@code PaiementRefuse} sinon. */
    EvenementPaiement lire(byte[] corps, String signature);
}
