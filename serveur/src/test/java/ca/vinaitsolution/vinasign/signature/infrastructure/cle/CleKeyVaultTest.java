package ca.vinaitsolution.vinasign.signature.infrastructure.cle;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.azure.security.keyvault.keys.cryptography.CryptographyClient;
import com.azure.security.keyvault.keys.models.KeyProperties;
import com.azure.security.keyvault.keys.models.KeyType;
import com.azure.security.keyvault.keys.models.KeyVaultKey;
import java.security.cert.X509Certificate;
import java.util.List;
import org.bouncycastle.asn1.ASN1Integer;
import org.bouncycastle.asn1.ASN1Sequence;
import org.junit.jupiter.api.Test;

/** Règles de la clé de cachet en HSM (VS-031), sans Key Vault : clé exportable ou de type inconnu refusées, signature ECDSA en DER. */
class CleKeyVaultTest {

    private static CryptographyClient client(Boolean exportable, KeyType type) {
        KeyProperties proprietes = mock(KeyProperties.class);
        when(proprietes.isExportable()).thenReturn(exportable);
        KeyVaultKey cle = mock(KeyVaultKey.class);
        when(cle.getProperties()).thenReturn(proprietes);
        when(cle.getKeyType()).thenReturn(type);
        CryptographyClient c = mock(CryptographyClient.class);
        when(c.getKey()).thenReturn(cle);
        return c;
    }

    @Test
    void une_cle_exportable_ou_sans_certificat_est_refusee_au_demarrage() {
        List<X509Certificate> chaine = List.of(mock(X509Certificate.class));
        assertTrue(assertThrows(IllegalStateException.class, () -> new CleKeyVault(client(true, KeyType.RSA_HSM), chaine)).getMessage()
                .contains("exportable"));
        assertThrows(IllegalStateException.class, () -> new CleKeyVault(client(false, KeyType.RSA_HSM), List.of()));
        assertThrows(IllegalStateException.class, () -> new CleKeyVault(client(null, KeyType.EC_HSM), null));
        assertEquals(chaine, new CleKeyVault(client(null, KeyType.EC_HSM), chaine).chaine());
    }

    @Test
    void seuls_rsa_et_ec_sont_pris_en_charge() {
        assertThrows(IllegalStateException.class, () -> CleKeyVault.algorithme(KeyType.OCT));
        assertThrows(IllegalStateException.class, () -> CleKeyVault.algorithme(KeyType.OCT_HSM));
    }

    @Test
    void la_signature_brute_r_s_est_encodee_en_der() {
        byte[] brute = new byte[64];
        brute[0] = (byte) 0x80;
        brute[63] = 1;
        ASN1Sequence seq = ASN1Sequence.getInstance(CleKeyVault.der(brute));
        assertEquals(new java.math.BigInteger(1, java.util.Arrays.copyOfRange(brute, 0, 32)), ((ASN1Integer) seq.getObjectAt(0)).getValue(),
                "bit de poids fort : zéro de tête ajouté, valeur positive");
        assertEquals(java.math.BigInteger.ONE, ((ASN1Integer) seq.getObjectAt(1)).getValue());
    }
}
