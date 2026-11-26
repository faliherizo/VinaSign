package ca.vinaitsolution.vinasign.signature.caracterisation;

/**
 * Comportement observable d'un moteur de cachet, tel que la caractérisation l'éprouve (VS-030) : le moteur du CLM et
 * celui de VinaSign (VS-031, VS-032) s'y adaptent chacun, et la même suite juge les deux.
 */
public interface CachetSousEssai {

    /** PDF scellé (signature intégrée couvrant tout le document, données de validation, horodatage de document). */
    byte[] scellerPdf(byte[] pdf, String motif);

    /** Signature CMS détachée, DER. */
    byte[] signerDetache(byte[] contenu);

    /** Niveau ETSI annoncé pour {@link #scellerPdf}. */
    String niveauPades();
}
