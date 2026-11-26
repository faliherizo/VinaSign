package ca.vinaitsolution.vinasign.commun.application.port;

/**
 * Scellement d'un document par le cachet de la plateforme (VS-070) : signature PAdES intégrée, données de validation et
 * horodatage de document quand une autorité est configurée.
 */
public interface ScellementDocuments {

    byte[] scellerPdf(byte[] pdf, String motif);

    /** Signature CMS détachée d'une empreinte SHA-256, horodatée si une autorité est configurée (ancrage du journal, VS-071). */
    byte[] signerEmpreinte(byte[] sha256);
}
