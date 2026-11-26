package ca.vinaitsolution.vinasign.enveloppe.application.port;

/**
 * Conversion d'un document bureautique (Word, OpenDocument) en PDF/A-2b (VS-035), par un service isolé du réseau.
 * {@code DocumentRefuse} si le document ne peut pas être converti.
 */
public interface ConversionPdfA {

    byte[] convertir(byte[] document, String nomFichier);
}
