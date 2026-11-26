package ca.vinaitsolution.vinasign.enveloppe.infrastructure;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** Document Word minimal (OOXML) produit à chaque exécution : jamais de fichier binaire figé dans le dépôt. */
public final class Bureautique {

    private Bureautique() {
    }

    public static byte[] docx(String texte) {
        try (ByteArrayOutputStream octets = new ByteArrayOutputStream(); ZipOutputStream zip = new ZipOutputStream(octets)) {
            ecrire(zip, "[Content_Types].xml", """
                    <?xml version="1.0" encoding="UTF-8"?><Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
                    <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
                    <Default Extension="xml" ContentType="application/xml"/>
                    <Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/></Types>""");
            ecrire(zip, "_rels/.rels", """
                    <?xml version="1.0" encoding="UTF-8"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
                    <Relationship Id="r1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/></Relationships>""");
            ecrire(zip, "word/document.xml", """
                    <?xml version="1.0" encoding="UTF-8"?><w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
                    <w:body><w:p><w:r><w:t>%s</w:t></w:r></w:p></w:body></w:document>""".formatted(texte));
            zip.finish();
            return octets.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void ecrire(ZipOutputStream zip, String nom, String contenu) throws IOException {
        zip.putNextEntry(new ZipEntry(nom));
        zip.write(contenu.strip().getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }
}
