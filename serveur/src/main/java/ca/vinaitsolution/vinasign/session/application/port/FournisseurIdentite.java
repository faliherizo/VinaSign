package ca.vinaitsolution.vinasign.session.application.port;

/**
 * Fournisseur de vérification d'identité (VS-131) : le signataire est envoyé chez lui avec une référence ; il rappelle la
 * plateforme avec le résultat, authentifié (signature du rappel). Le choix du fournisseur est une configuration.
 */
public interface FournisseurIdentite {

    /** @param reference secret de 256 bits qui relie le rappel à la vérification */
    record Demande(String reference, String langue) {
    }

    /** Résultat authentifié : méthode (pièce, banque…) et niveau d'assurance déclarés par le fournisseur. */
    record Conclusion(String reference, boolean reussie, String methode, String assurance) {
    }

    /** Code du fournisseur, consigné dans la preuve. */
    String code();

    /** Adresse où envoyer le signataire. {@code VerificationIdentiteIndisponible} si aucun fournisseur n'est configuré. */
    String demarrer(Demande demande);

    /** Rappel authentifié et lu ; {@code VerificationIdentiteRefusee} si sa signature ne vérifie pas ou s'il est illisible. */
    Conclusion conclure(byte[] corps, String signature);
}
