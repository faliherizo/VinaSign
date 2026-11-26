package ca.vinaitsolution.vinasign.session.application.port;

/** Vérification des cérémonies WebAuthn (VS-043) ; lève {@code CleAccesRefusee} si la réponse de l'authentificateur est invalide. */
public interface VerificationWebAuthn {

    /** Défi émis, origine de l'interface du signataire, identifiant de la partie de confiance. */
    record Ceremonie(byte[] defi, String origine, String rpId) {
    }

    record Enregistree(String credentialId, byte[] donnees, long compteur) {
    }

    Enregistree enregistrer(Ceremonie c, byte[] attestationObject, byte[] clientDataJson);

    /** Rend le nouveau compteur de signatures de l'authentificateur. */
    long authentifier(Ceremonie c, byte[] donneesCle, long compteur, Assertion assertion);

    record Assertion(byte[] credentialId, byte[] authenticatorData, byte[] clientDataJson, byte[] signature) {
    }
}
