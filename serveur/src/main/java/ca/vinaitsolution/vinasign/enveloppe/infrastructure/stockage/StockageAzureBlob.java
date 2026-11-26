package ca.vinaitsolution.vinasign.enveloppe.infrastructure.stockage;

import ca.vinaitsolution.vinasign.enveloppe.application.port.StockageDocuments;
import ca.vinaitsolution.vinasign.enveloppe.domain.VersionImmuable;
import com.azure.core.util.BinaryData;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.models.BlobErrorCode;
import com.azure.storage.blob.models.BlobRequestConditions;
import com.azure.storage.blob.models.BlobStorageException;
import com.azure.storage.blob.options.BlobParallelUploadOptions;

/**
 * Documents dans Azure Blob (conteneur privé). L'écriture exige l'absence de l'objet ({@code If-None-Match: *}) : un
 * objet écrit ne se réécrit jamais (VS-022), même par une requête concurrente.
 */
public class StockageAzureBlob implements StockageDocuments {

    private final BlobContainerClient conteneur;

    public StockageAzureBlob(BlobContainerClient conteneur) {
        this.conteneur = conteneur;
    }

    @Override
    public void ecrire(String cle, byte[] contenu) {
        try {
            conteneur.getBlobClient(cle).uploadWithResponse(new BlobParallelUploadOptions(BinaryData.fromBytes(contenu))
                    .setRequestConditions(new BlobRequestConditions().setIfNoneMatch("*")), null, null);
        } catch (BlobStorageException e) {
            if (BlobErrorCode.BLOB_ALREADY_EXISTS.equals(e.getErrorCode()) || e.getStatusCode() == 409) {
                throw new VersionImmuable();
            }
            throw e;
        }
    }

    @Override
    public byte[] lire(String cle) {
        return conteneur.getBlobClient(cle).downloadContent().toBytes();
    }
}
