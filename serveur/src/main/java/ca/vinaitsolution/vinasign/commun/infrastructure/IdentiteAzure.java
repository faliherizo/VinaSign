package ca.vinaitsolution.vinasign.commun.infrastructure;

import com.azure.identity.DefaultAzureCredentialBuilder;
import com.azure.security.keyvault.keys.cryptography.CryptographyClient;
import com.azure.security.keyvault.keys.cryptography.CryptographyClientBuilder;
import com.azure.storage.blob.BlobServiceClientBuilder;

/**
 * Clients Azure authentifiés par l'identité gérée de la plateforme (exploitation seulement ; sur un poste et en test,
 * Key Vault et le stockage sont joints par l'émulateur et une chaîne de connexion). Exclue de la mesure de couverture,
 * nominativement : elle ne fait que construire des clients avec l'identité de l'environnement, ce que seul un
 * déploiement réel éprouve.
 */
public final class IdentiteAzure {

    private IdentiteAzure() {
    }

    public static CryptographyClient cryptographie(String identifiantCle) {
        return new CryptographyClientBuilder().keyIdentifier(identifiantCle).credential(new DefaultAzureCredentialBuilder().build()).buildClient();
    }

    public static BlobServiceClientBuilder stockage(String pointDeTerminaison) {
        return new BlobServiceClientBuilder().endpoint(pointDeTerminaison).credential(new DefaultAzureCredentialBuilder().build());
    }
}
