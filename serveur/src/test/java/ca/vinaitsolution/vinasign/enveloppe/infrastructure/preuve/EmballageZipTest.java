package ca.vinaitsolution.vinasign.enveloppe.infrastructure.preuve;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.SequencedMap;
import java.util.zip.ZipInputStream;
import org.junit.jupiter.api.Test;

class EmballageZipTest {

    @Test
    void l_archive_garde_l_ordre_des_fichiers_et_une_ecriture_interrompue_echoue() throws Exception {
        EmballageZip e = new EmballageZip(new ObjectMapper());
        SequencedMap<String, byte[]> fichiers = new LinkedHashMap<>();
        fichiers.put("b.txt", "2".getBytes());
        fichiers.put("a.json", e.json(Map.of("x", 1)));
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(e.zip(fichiers)))) {
            assertEquals("b.txt", zip.getNextEntry().getName());
            assertEquals("a.json", zip.getNextEntry().getName());
            assertTrue(new String(zip.readAllBytes()).contains("\n"), "JSON indenté, lisible");
        }
        OutputStream disquePlein = new OutputStream() {
            @Override
            public void write(int b) throws IOException {
                throw new IOException("disque plein");
            }
        };
        assertThrows(UncheckedIOException.class, () -> EmballageZip.archiver(fichiers, disquePlein));
    }
}
