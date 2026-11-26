package ca.vinaitsolution.vinasign.signature.infrastructure.cle;

/** Contrôle commun des adaptateurs : on ne signe qu'une empreinte SHA-256, jamais un contenu. */
final class Empreintes {

    private Empreintes() {
    }

    static void exigerSha256(byte[] empreinte) {
        if (empreinte == null || empreinte.length != 32) {
            throw new IllegalArgumentException("Seule une empreinte SHA-256 (32 octets) se signe.");
        }
    }
}
