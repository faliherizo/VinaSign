package ca.vinaitsolution.vinasign.signature.caracterisation;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.JsonNode;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Suite de caractérisation du moteur de cachet (VS-030, VS-ADR-002). Les juges sont externes, jamais le code éprouvé :
 * pyHanko pour PAdES-B-LTA, {@code openssl cms} pour CAdES, veraPDF pour PDF/A. Exécutée contre le moteur du CLM
 * aujourd'hui, contre celui de VinaSign dès son portage (VS-031, VS-032) : la même suite au vert pour les deux est la
 * condition de la substitution.
 */
@Tag("caracterisation")
public abstract class CaracterisationCachet {

    private static final String HTML = """
            <!doctype html><html lang="fr"><head><meta charset="utf-8"><title>Convention d'essai</title></head>
            <body><h1>Convention d'essai</h1><p>Montant : 250 000 $ — « garantie » ; signée électroniquement.</p></body></html>""";

    private CachetSousEssai cachet;

    protected abstract CachetSousEssai cachet() throws Exception;

    @BeforeAll
    static void bacsASable() {
        BacsASable.exiger();
    }

    @BeforeEach
    void preparer() throws Exception {
        cachet = cachet();
    }

    @Test
    void le_pdf_scelle_est_juge_valide_par_pyhanko_au_niveau_pades_b_lta() throws Exception {
        assertEquals("PAdES-B-LTA", cachet.niveauPades());
        JsonNode verdict = BacsASable.pyhanko(cachet.scellerPdf(BacsASable.pdfA(HTML), "Caractérisation VS-030"), "pades-lta");
        assertTrue(verdict.get("valide").asBoolean(), verdict.get("rapport").asText());
    }

    @Test
    void une_alteration_du_pdf_scelle_est_detectee_par_pyhanko() throws Exception {
        byte[] scelle = cachet.scellerPdf(BacsASable.pdfA(HTML), "Caractérisation VS-030");
        byte[] altere = scelle.clone();
        altere[altere.length / 4] ^= 0x01;
        assertFalse(BacsASable.pyhanko(altere, "pades").get("valide").asBoolean(), "un octet changé invalide le scellement");
    }

    @Test
    void la_signature_detachee_est_verifiee_par_openssl_et_refuse_un_contenu_altere() throws Exception {
        byte[] contenu = "Note clinique — empreinte à sceller".getBytes(StandardCharsets.UTF_8);
        byte[] signature = cachet.signerDetache(contenu);
        JsonNode verdict = BacsASable.opensslCms(signature, contenu);
        assertTrue(verdict.get("valide").asBoolean(), verdict.get("rapport").asText());
        assertFalse(BacsASable.opensslCms(signature, "Note clinique — altérée".getBytes(StandardCharsets.UTF_8)).get("valide").asBoolean());
    }

    @Test
    void le_scellement_preserve_la_conformite_pdf_a_jugee_par_verapdf() throws Exception {
        byte[] original = BacsASable.pdfA(HTML);
        assertTrue(conforme(BacsASable.verapdf(original)), "témoin : le document d'entrée est PDF/A-2b");
        JsonNode rapport = BacsASable.verapdf(cachet.scellerPdf(original, "Caractérisation VS-030"));
        assertTrue(conforme(rapport), rapport.toString());
    }

    /** Le rapport JSON de veraPDF REST porte « isCompliant » sur le résultat de validation. */
    private static boolean conforme(JsonNode rapport) {
        return rapport.findValues("isCompliant").stream().anyMatch(JsonNode::asBoolean)
                || rapport.findValues("compliant").stream().anyMatch(JsonNode::asBoolean);
    }
}
