package ca.vinaitsolution.vinasign.signature.application.port;

import java.security.cert.X509Certificate;

/**
 * Cachet électronique d'une organisation (VS-032), porté du CLM (VS-ADR-002) : signature CMS détachée (CAdES) et
 * scellement PAdES d'un PDF, vérifiables par des outils indépendants (pyHanko, OpenSSL).
 */
public interface Cachet {

    /** Signature CMS détachée, DER ({@code .p7s}), horodatée si une autorité est configurée. */
    byte[] signerDetache(byte[] contenu);

    /**
     * Signature CMS détachée d'une empreinte SHA-256 (VS-034) : le document n'est jamais transmis ; la signature se
     * vérifie avec le document d'origine ({@code openssl cms -verify -content document}).
     */
    byte[] signerEmpreinte(byte[] sha256);

    /** PDF augmenté d'une signature intégrée couvrant tout le document, des données de validation (DSS) et d'un horodatage de document. */
    byte[] scellerPdf(byte[] pdf, String motif);

    /** VS-132 : données de validation à jour et nouvel horodatage de document, en mise à jour incrémentale. */
    byte[] renouvelerHorodatage(byte[] pdf);

    /** VS-132 : fin de validité du certificat de l'autorité du dernier horodatage de document ; vide s'il n'y en a aucun. */
    java.util.Optional<java.time.Instant> echeanceHorodatage(byte[] pdf);

    /** « PAdES-B-LTA » avec une autorité d'horodatage, « PAdES-B-B » sinon. */
    String niveauPades();

    /** Vrai pour le certificat du cachet ou pour un certificat renouvelé au même titulaire et émis par la même autorité. */
    boolean reconnait(X509Certificate certificat);

    /** Titulaire du certificat de cachet (nom distinctif X.500). */
    String titulaire();
}
