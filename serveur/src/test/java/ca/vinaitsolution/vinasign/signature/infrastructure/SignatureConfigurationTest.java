package ca.vinaitsolution.vinasign.signature.infrastructure;

import static org.junit.jupiter.api.Assertions.*;

import ca.vinaitsolution.vinasign.signature.cle.Certificats;
import ca.vinaitsolution.vinasign.signature.domain.AlgorithmeSignature;
import ca.vinaitsolution.vinasign.signature.infrastructure.SignatureConfiguration.CachetProprietes;
import java.nio.file.Path;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SignatureConfigurationTest {

    private final SignatureConfiguration configuration = new SignatureConfiguration();

    @Test
    void un_magasin_pkcs12_est_refuse_en_production_et_accepte_ailleurs(@TempDir Path dossier) {
        String magasin = Certificats.magasin(dossier, "RSA", "essai".toCharArray()).toString();
        assertThrows(IllegalStateException.class,
                () -> configuration.cleDeSignature(new CachetProprietes("production", "pkcs12", null, null, magasin, "essai", null)));
        assertEquals(AlgorithmeSignature.RSA_SHA256,
                configuration.cleDeSignature(new CachetProprietes("bac-a-sable", "pkcs12", null, null, magasin, "essai", null)).algorithme());
        assertThrows(IllegalStateException.class,
                () -> configuration.cleDeSignature(new CachetProprietes("poste", "pkcs12", null, null, magasin, null, null)), "mot de passe requis");
    }

    @Test
    void le_fournisseur_key_vault_ouvre_la_cle_du_coffre_avec_sa_chaine() throws Exception {
        var cert = Certificats.pour(Certificats.paire("RSA").getPublic());
        String pem = "-----BEGIN CERTIFICATE-----\n" + Base64.getMimeEncoder().encodeToString(cert.getEncoded()) + "\n-----END CERTIFICATE-----\n";
        var proprietes = org.mockito.Mockito.mock(com.azure.security.keyvault.keys.models.KeyProperties.class);
        var cle = org.mockito.Mockito.mock(com.azure.security.keyvault.keys.models.KeyVaultKey.class);
        org.mockito.Mockito.when(cle.getProperties()).thenReturn(proprietes);
        org.mockito.Mockito.when(cle.getKeyType()).thenReturn(com.azure.security.keyvault.keys.models.KeyType.RSA_HSM);
        var client = org.mockito.Mockito.mock(com.azure.security.keyvault.keys.cryptography.CryptographyClient.class);
        org.mockito.Mockito.when(client.getKey()).thenReturn(cle);
        java.util.List<String> demandees = new java.util.ArrayList<>();
        var cachet = new SignatureConfiguration(id -> {
            demandees.add(id);
            return client;
        }).cleDeSignature(new CachetProprietes("production", "key-vault", "https://coffre.vault.azure.net/keys/cachet/1", pem, null, null, null));
        assertEquals(AlgorithmeSignature.RSA_SHA256, cachet.algorithme());
        assertEquals(java.util.List.of("https://coffre.vault.azure.net/keys/cachet/1"), demandees);
        assertEquals(cert, cachet.chaine().get(0));
    }

    @Test
    void sans_fournisseur_le_serveur_demarre_mais_ne_scelle_rien() {
        var absente = configuration.cleDeSignature(new CachetProprietes("poste", null, null, null, null, null, null));
        assertTrue(absente.chaine().isEmpty());
        assertThrows(IllegalStateException.class, () -> absente.signerEmpreinte(new byte[32]));
        assertThrows(IllegalStateException.class, absente::algorithme);
        assertFalse(new CachetProprietes("poste", "pkcs12", null, null, "m", "secret", null).toString().contains("secret"));
    }

    @Test
    void la_chaine_pem_se_lit_et_une_chaine_illisible_est_signalee() throws Exception {
        var cert = Certificats.pour(Certificats.paire("RSA").getPublic());
        String pem = "-----BEGIN CERTIFICATE-----\n" + Base64.getMimeEncoder().encodeToString(cert.getEncoded()) + "\n-----END CERTIFICATE-----\n";
        assertEquals(cert, SignatureConfiguration.certificats(pem).get(0));
        assertTrue(SignatureConfiguration.certificats(" ").isEmpty());
        assertTrue(SignatureConfiguration.certificats(null).isEmpty());
        assertThrows(IllegalStateException.class, () -> SignatureConfiguration.certificats("-----BEGIN CERTIFICATE-----\nAAAA\n-----END CERTIFICATE-----"));
    }

    @Test
    void le_fournisseur_key_vault_exige_un_identifiant_de_cle_valide() {
        assertThrows(IllegalArgumentException.class,
                () -> configuration.cleDeSignature(new CachetProprietes("production", "key-vault", "pas-une-url", null, null, null, null)));
    }
}
