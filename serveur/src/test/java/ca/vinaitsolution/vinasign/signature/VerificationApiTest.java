package ca.vinaitsolution.vinasign.signature;

import static org.junit.jupiter.api.Assertions.*;

import ca.vinaitsolution.vinasign.signature.cle.Certificats;
import ca.vinaitsolution.vinasign.signature.infrastructure.cle.ClePkcs12;
import ca.vinaitsolution.vinasign.signature.infrastructure.pades.CachetPades;
import ca.vinaitsolution.vinasign.signature.infrastructure.verification.VerificateurPadesTest;
import ca.vinaitsolution.vinasign.support.PostgresDeTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.time.Clock;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/** VS-036 — vérification publique, sans compte, d'un PDF signé ou d'une empreinte. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(PostgresDeTest.class)
class VerificationApiTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    private static CachetPades cachet() throws Exception {
        return new CachetPades(new ClePkcs12(Certificats.magasin(Files.createTempDirectory("v"), "RSA", "s".toCharArray()), "s".toCharArray()),
                null, Clock.systemUTC());
    }

    @Test
    void un_tiers_verifie_un_pdf_signe_sans_compte() throws Exception {
        MockHttpServletResponse r = mvc.perform(MockMvcRequestBuilders.post("/api/v1/verifications").contentType("application/pdf")
                .content(cachet().scellerPdf(VerificateurPadesTest.pdf(), "x"))).andReturn().getResponse();
        assertEquals(200, r.getStatus(), r.getContentAsString());
        JsonNode rapport = json.readTree(r.getContentAsString());
        assertTrue(rapport.get("integre").asBoolean());
        assertEquals("PAdES-B-B", rapport.get("niveau").asText());
        assertEquals(400, mvc.perform(MockMvcRequestBuilders.post("/api/v1/verifications").contentType("application/pdf").content(new byte[] {1, 2}))
                .andReturn().getResponse().getStatus());
    }

    @Test
    void un_tiers_verifie_une_empreinte_et_sa_signature() throws Exception {
        byte[] sha = MessageDigest.getInstance("SHA-256").digest("note".getBytes());
        String corps = json.writeValueAsString(Map.of("sha256", HexFormat.of().formatHex(sha), "cms",
                Base64.getEncoder().encodeToString(cachet().signerEmpreinte(sha))));
        JsonNode rapport = json.readTree(mvc.perform(MockMvcRequestBuilders.post("/api/v1/verifications").contentType(MediaType.APPLICATION_JSON)
                .content(corps)).andReturn().getResponse().getContentAsString());
        assertTrue(rapport.get("integre").asBoolean());
        assertEquals(400, mvc.perform(MockMvcRequestBuilders.post("/api/v1/verifications").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("sha256", "z".repeat(64), "cms", "AAAA")))).andReturn().getResponse().getStatus());
        assertEquals(400, mvc.perform(MockMvcRequestBuilders.post("/api/v1/verifications").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("sha256", "a".repeat(64), "cms", "%%%")))).andReturn().getResponse().getStatus());
    }
}
