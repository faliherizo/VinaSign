package ca.vinaitsolution.vinasign.enveloppe.infrastructure.controle;

import ca.vinaitsolution.vinasign.enveloppe.infrastructure.Pdfs;

import static org.junit.jupiter.api.Assertions.*;

import ca.vinaitsolution.vinasign.enveloppe.domain.DocumentRefuse;
import ca.vinaitsolution.vinasign.enveloppe.domain.VersionImmuable;
import ca.vinaitsolution.vinasign.enveloppe.infrastructure.stockage.StockageAzureBlob;
import ca.vinaitsolution.vinasign.support.StockageDeTest;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobServiceClientBuilder;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Adaptateurs de contrôle et de stockage contre leurs dépendances réelles (ClamAV, Azurite), sans contexte Spring. */
class ControlesDocumentsTest {

    /** Fichier de test antivirus normalisé EICAR : inoffensif, détecté par tout antivirus. Assemblé ici, jamais stocké tel quel. */
    private static final String EICAR = "X5O!P%@AP[4\\PZX54(P^)7CC)7}$" + "EICAR-STANDARD-ANTIVIRUS-TEST-FILE!$H+H*";

    private final AntivirusClamd antivirus = new AntivirusClamd(StockageDeTest.CLAMAV.getHost(), StockageDeTest.CLAMAV.getMappedPort(3310),
            Duration.ofSeconds(30));

    @Test
    void clamav_reconnait_un_contenu_sain_et_nomme_une_menace() {
        assertEquals(Optional.empty(), antivirus.analyser(Pdfs.pages(1)));
        assertTrue(antivirus.analyser(EICAR.getBytes(StandardCharsets.US_ASCII)).orElseThrow().toLowerCase().contains("eicar"));
    }

    @Test
    void une_analyse_impossible_n_est_jamais_un_contenu_sain() {
        assertThrows(UncheckedIOException.class, () -> new AntivirusClamd("127.0.0.1", 1, Duration.ofSeconds(1)).analyser(new byte[] {1}));
        assertThrows(IllegalStateException.class, () -> AntivirusClamd.interpreter("INSTREAM size limit exceeded. ERROR"));
        assertEquals(Optional.of("Win.Test"), AntivirusClamd.interpreter("stream: Win.Test FOUND"));
    }

    @Test
    void la_structure_d_un_pdf_est_lue_et_un_pdf_chiffre_ou_illisible_refuse() {
        InspectionPdfBox inspection = new InspectionPdfBox(50_000_000);
        assertEquals(3, inspection.pages(Pdfs.pages(3)));
        assertEquals("document-chiffre", assertThrows(DocumentRefuse.class, () -> inspection.pages(Pdfs.chiffre())).type());
        assertEquals("document-chiffre", assertThrows(DocumentRefuse.class, () -> inspection.pages(Pdfs.protegeSansMotDePasse())).type(),
                "chiffré même sans mot de passe d'ouverture");
        assertEquals("document-structure", assertThrows(DocumentRefuse.class,
                () -> inspection.pages("%PDF-1.7 tronqué".getBytes(StandardCharsets.US_ASCII))).type());
        assertEquals("document-structure", assertThrows(DocumentRefuse.class, () -> inspection.pages(Pdfs.pages(0))).type());
    }

    @Test
    void un_objet_stocke_se_relit_et_ne_se_reecrit_jamais() {
        BlobContainerClient conteneur = new BlobServiceClientBuilder().connectionString(StockageDeTest.AZURITE.getConnectionString()).buildClient()
                .getBlobContainerClient("essai-" + UUID.randomUUID());
        conteneur.create();
        StockageAzureBlob stockage = new StockageAzureBlob(conteneur);
        stockage.ecrire("a/b", "premier".getBytes(StandardCharsets.UTF_8));
        assertEquals("premier", new String(stockage.lire("a/b"), StandardCharsets.UTF_8));
        assertThrows(VersionImmuable.class, () -> stockage.ecrire("a/b", "second".getBytes(StandardCharsets.UTF_8)));
        assertEquals("premier", new String(stockage.lire("a/b"), StandardCharsets.UTF_8), "l'original est intact");
        assertThrows(com.azure.storage.blob.models.BlobStorageException.class,
                () -> new StockageAzureBlob(conteneur.getServiceClient().getBlobContainerClient("absent-" + UUID.randomUUID()))
                        .ecrire("x", new byte[] {1}), "une autre erreur du stockage n'est pas déguisée");
    }

    @Test
    void un_document_word_est_converti_en_pdf_a_conforme_selon_verapdf() throws Exception {
        ConversionGotenberg conversion = new ConversionGotenberg(StockageDeTest.conversion() + "/", Duration.ofMinutes(2));
        byte[] pdf = conversion.convertir(ca.vinaitsolution.vinasign.enveloppe.infrastructure.Bureautique.docx("Convention — « garantie »"), "convention.docx");
        assertEquals(1, new InspectionPdfBox(50_000_000).pages(pdf));
        String rapport = StockageDeTest.rapportPdfA2b(pdf);
        assertTrue(StockageDeTest.conformePdfA2b(rapport), rapport.substring(0, Math.min(800, rapport.length())));
        assertEquals("document-conversion", assertThrows(DocumentRefuse.class,
                () -> conversion.convertir("PK\u0003\u0004 pas un document".getBytes(StandardCharsets.ISO_8859_1), "x.docx")).type());
        assertThrows(IllegalStateException.class, () -> new ConversionGotenberg("http://127.0.0.1:1", Duration.ofSeconds(2)).convertir(new byte[] {1}, "x"));
        Thread.currentThread().interrupt();
        try {
            assertThrows(IllegalStateException.class, () -> conversion.convertir(new byte[] {1}, "x.docx"), "attente interrompue");
            assertTrue(Thread.currentThread().isInterrupted(), "l'interruption est conservée pour l'appelant");
        } finally {
            Thread.interrupted();
        }
    }
}
