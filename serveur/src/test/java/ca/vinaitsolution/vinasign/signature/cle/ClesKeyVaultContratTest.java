package ca.vinaitsolution.vinasign.signature.cle;

import static org.junit.jupiter.api.Assertions.*;

import ca.vinaitsolution.vinasign.signature.application.port.CleDeSignature;
import ca.vinaitsolution.vinasign.signature.infrastructure.cle.CleKeyVault;
import com.azure.security.keyvault.keys.KeyClient;
import com.azure.security.keyvault.keys.models.CreateEcKeyOptions;
import com.azure.security.keyvault.keys.models.CreateRsaKeyOptions;
import com.azure.security.keyvault.keys.models.KeyCurveName;
import com.azure.security.keyvault.keys.models.KeyOperation;
import com.azure.security.keyvault.keys.models.KeyVaultKey;
import com.github.nagyesta.lowkeyvault.testcontainers.LowkeyVaultContainer;
import com.github.nagyesta.lowkeyvault.testcontainers.LowkeyVaultContainerBuilder;
import java.security.PublicKey;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** Adaptateur Key Vault contre un émulateur de Key Vault (Lowkey Vault) : le vrai SDK Azure, les vraies opérations. */
class ClesKeyVaultContratTest {

    @SuppressWarnings("resource")
    static final LowkeyVaultContainer COFFRE = LowkeyVaultContainerBuilder.lowkeyVault("nagyesta/lowkey-vault:3.1.31").build();

    static {
        COFFRE.start();
    }

    static KeyClient cles() {
        return COFFRE.getClientFactory().getKeyClientBuilderForDefaultVault()
                .serviceVersion(com.azure.security.keyvault.keys.KeyServiceVersion.V7_4).buildClient();
    }

    static CleDeSignature adaptateur(KeyVaultKey cle, PublicKey publique) {
        return new CleKeyVault(COFFRE.getClientFactory().getCryptoClientBuilderForDefaultVault().keyIdentifier(cle.getId())
                .serviceVersion(com.azure.security.keyvault.keys.cryptography.CryptographyServiceVersion.V7_4).buildClient(),
                List.of(Certificats.pour(publique)));
    }

    @Nested
    class Rsa extends ContratCleDeSignature {
        @Override
        protected CleDeSignature cle() {
            KeyVaultKey k = cles().createRsaKey(new CreateRsaKeyOptions("cachet-rsa-" + UUID.randomUUID()).setKeySize(2048).setKeyOperations(KeyOperation.SIGN, KeyOperation.VERIFY));
            return adaptateur(k, k.getKey().toRsa(false).getPublic());
        }
    }

    @Nested
    class Ec extends ContratCleDeSignature {
        @Override
        protected CleDeSignature cle() {
            KeyVaultKey k = cles().createEcKey(new CreateEcKeyOptions("cachet-ec-" + UUID.randomUUID()).setCurveName(KeyCurveName.P_256).setKeyOperations(KeyOperation.SIGN, KeyOperation.VERIFY));
            return adaptateur(k, k.getKey().toEc(false).getPublic());
        }
    }

    @Test
    void sans_certificat_du_cachet_l_adaptateur_refuse_de_demarrer() {
        KeyVaultKey k = cles().createRsaKey(new CreateRsaKeyOptions("sans-certificat-" + UUID.randomUUID()));
        var crypto = COFFRE.getClientFactory().getCryptoClientBuilderForDefaultVault().keyIdentifier(k.getId())
                .serviceVersion(com.azure.security.keyvault.keys.cryptography.CryptographyServiceVersion.V7_4).buildClient();
        assertThrows(IllegalStateException.class, () -> new CleKeyVault(crypto, List.of()));
        assertThrows(IllegalStateException.class, () -> new CleKeyVault(crypto, null));
    }
}
