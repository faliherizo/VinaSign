package ca.vinaitsolution.vinasign.signature.infrastructure;

import ca.vinaitsolution.vinasign.commun.application.port.ActeurCourant;
import ca.vinaitsolution.vinasign.commun.application.port.GenerateurIdentifiant;
import ca.vinaitsolution.vinasign.commun.application.port.JournalAudit;
import ca.vinaitsolution.vinasign.commun.application.port.OrganisationCourante;
import ca.vinaitsolution.vinasign.signature.application.SignerEmpreinte;
import ca.vinaitsolution.vinasign.signature.application.VerifierDocument;
import ca.vinaitsolution.vinasign.signature.infrastructure.verification.VerificateurPades;
import ca.vinaitsolution.vinasign.signature.application.port.Cachet;
import ca.vinaitsolution.vinasign.signature.application.port.CleDeSignature;
import ca.vinaitsolution.vinasign.signature.infrastructure.pades.CachetPades;
import java.time.Clock;
import ca.vinaitsolution.vinasign.signature.infrastructure.cle.CleKeyVault;
import ca.vinaitsolution.vinasign.signature.infrastructure.cle.ClePkcs12;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Clé de cachet (VS-031) : le fournisseur est une configuration. {@code key-vault} en exploitation ; {@code pkcs12} sur
 * un poste ou un bac à sable seulement, refusé quand l'environnement déclaré est la production.
 */
@Configuration
@EnableConfigurationProperties(SignatureConfiguration.CachetProprietes.class)
public class SignatureConfiguration {

    /**
     * @param environnement environnement de déploiement (integration, preproduction, production, bac-a-sable, poste)
     * @param fournisseur   key-vault ou pkcs12 ; vide : aucune clé (le serveur démarre sans pouvoir sceller)
     * @param cleKeyVault   identifiant de la clé dans Key Vault (URL, version comprise)
     * @param chainePem     certificat du cachet puis intermédiaires, en PEM (émis par l'AC partenaire)
     * @param magasin       magasin PKCS#12 (poste, bac à sable)
     * @param urlHorodatage autorité d'horodatage RFC 3161 ; vide : PAdES-B-B
     */
    @ConfigurationProperties("vinasign.cachet")
    public record CachetProprietes(String environnement, String fournisseur, String cleKeyVault, String chainePem, String magasin,
                                   String motDePasse, String urlHorodatage) {

        @Override
        public String toString() {
            return "CachetProprietes[environnement=" + environnement + ", fournisseur=" + fournisseur + "]";
        }
    }

    static final String PRODUCTION = "production";

    /** Client du coffre pour un identifiant de clé : identité gérée en exploitation, remplaçable en test. */
    private final java.util.function.Function<String, com.azure.security.keyvault.keys.cryptography.CryptographyClient> coffre;

    public SignatureConfiguration() {
        this(ca.vinaitsolution.vinasign.commun.infrastructure.IdentiteAzure::cryptographie);
    }

    SignatureConfiguration(java.util.function.Function<String, com.azure.security.keyvault.keys.cryptography.CryptographyClient> coffre) {
        this.coffre = coffre;
    }

    @Bean
    CleDeSignature cleDeSignature(CachetProprietes p) {
        String fournisseur = p.fournisseur() == null ? "" : p.fournisseur();
        return switch (fournisseur) {
            case "key-vault" -> new CleKeyVault(coffre.apply(p.cleKeyVault()), certificats(p.chainePem()));
            case "pkcs12" -> pkcs12(p);
            default -> new CleAbsente();
        };
    }

    static CleDeSignature pkcs12(CachetProprietes p) {
        if (PRODUCTION.equals(p.environnement())) {
            throw new IllegalStateException("Magasin PKCS#12 refusé en production : la clé de cachet est une clé de HSM (Key Vault).");
        }
        return new ClePkcs12(Path.of(p.magasin()), p.motDePasse() == null ? new char[0] : p.motDePasse().toCharArray());
    }

    /** Cachet de la plateforme (VS-032) ; l'horodatage vient de la configuration (VS-033). */
    @Bean
    Cachet cachet(CleDeSignature cle, CachetProprietes p, Clock horloge) {
        return cle.chaine().isEmpty() ? new CachetIndisponible() : new CachetPades(cle, p.urlHorodatage(), horloge);
    }

    @Bean
    SignerEmpreinte signerEmpreinte(Cachet cachet, OrganisationCourante organisation, ActeurCourant acteur, JournalAudit audit,
                                    GenerateurIdentifiant identifiants, Clock horloge) {
        return new SignerEmpreinte(cachet, organisation, acteur, audit, identifiants, horloge);
    }

    @Bean
    VerifierDocument verifierDocument(org.springframework.core.env.Environment env) {
        return new VerifierDocument(new VerificateurPades(), env.getRequiredProperty("vinasign.enveloppes.taille-max", Long.class));
    }

    static List<X509Certificate> certificats(String pem) {
        if (pem == null || pem.isBlank()) {
            return List.of();
        }
        try {
            return CertificateFactory.getInstance("X.509").generateCertificates(new ByteArrayInputStream(pem.getBytes(StandardCharsets.US_ASCII)))
                    .stream().map(X509Certificate.class::cast).toList();
        } catch (CertificateException e) {
            throw new IllegalStateException("Chaîne de certificats du cachet illisible.", e);
        }
    }
}
