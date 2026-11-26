package ca.vinaitsolution.vinasign.enveloppe.infrastructure.preuve;

import ca.vinaitsolution.vinasign.enveloppe.application.port.EmballagePreuve;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Map;
import java.util.SequencedMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.springframework.stereotype.Component;

/** JSON indenté (Jackson) et archive ZIP (porté de {@code PreuveService.archiver} du CLM) : jamais d'archive partielle. */
@Component
class EmballageZip implements EmballagePreuve {

    private final ObjectMapper json;

    EmballageZip(ObjectMapper json) {
        this.json = json;
    }

    @Override
    public byte[] json(Object valeur) {
        return ca.vinaitsolution.vinasign.commun.infrastructure.Json.ecrire(json.copy().enable(com.fasterxml.jackson.databind.SerializationFeature.INDENT_OUTPUT),
                valeur, "Contenu du dossier de preuve").getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }

    @Override
    public byte[] zip(SequencedMap<String, byte[]> fichiers) {
        ByteArrayOutputStream sortie = new ByteArrayOutputStream();
        archiver(fichiers, sortie);
        return sortie.toByteArray();
    }

    /** Écrit l'archive ; une écriture interrompue ne livre jamais d'archive partielle : l'erreur remonte. */
    static void archiver(SequencedMap<String, byte[]> fichiers, java.io.OutputStream sortie) {
        try (ZipOutputStream zip = new ZipOutputStream(sortie)) {
            for (Map.Entry<String, byte[]> f : fichiers.entrySet()) {
                zip.putNextEntry(new ZipEntry(f.getKey()));
                zip.write(f.getValue());
                zip.closeEntry();
            }
            zip.finish();
        } catch (IOException e) {
            throw new UncheckedIOException("Archive de preuve impossible.", e);
        }
    }
}
