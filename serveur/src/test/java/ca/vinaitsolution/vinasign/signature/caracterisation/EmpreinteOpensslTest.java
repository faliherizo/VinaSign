package ca.vinaitsolution.vinasign.signature.caracterisation;

import static org.junit.jupiter.api.Assertions.*;

import ca.vinaitsolution.vinasign.signature.infrastructure.cle.ClePkcs12;
import ca.vinaitsolution.vinasign.signature.infrastructure.pades.CachetPades;
import com.fasterxml.jackson.databind.JsonNode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** VS-034 — la signature d'empreinte vérifiée par OpenSSL avec le document d'origine, juge indépendant (bac à sable). */
@Tag("caracterisation")
class EmpreinteOpensslTest {

    @BeforeAll
    static void bacsASable() {
        BacsASable.exiger();
    }

    @Test
    void openssl_verifie_le_cms_d_une_empreinte_avec_le_document_et_refuse_un_autre() throws Exception {
        CachetPades cachet = new CachetPades(new ClePkcs12(BacsASable.magasinCachet(), BacsASable.motDePasseCachet().toCharArray()),
                BacsASable.icp() + "/tsa", Clock.systemUTC());
        byte[] document = "Note clinique du SAD".getBytes(StandardCharsets.UTF_8);
        byte[] cms = cachet.signerEmpreinte(MessageDigest.getInstance("SHA-256").digest(document));
        JsonNode verdict = BacsASable.opensslCms(cms, document);
        assertTrue(verdict.get("valide").asBoolean(), verdict.get("rapport").asText());
        assertFalse(BacsASable.opensslCms(cms, "autre".getBytes(StandardCharsets.UTF_8)).get("valide").asBoolean());
    }
}
