package ca.vinaitsolution.vinasign.signature;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

import ca.vinaitsolution.vinasign.signature.cle.Certificats;
import ca.vinaitsolution.vinasign.support.ApplicationsDeTest;
import ca.vinaitsolution.vinasign.support.ApplicationsDeTest.Application;
import ca.vinaitsolution.vinasign.support.PostgresDeTest;
import ca.vinaitsolution.vinasign.support.ReferentielsSql;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Map;
import javax.sql.DataSource;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cms.CMSProcessableByteArray;
import org.bouncycastle.cms.CMSSignedData;
import org.bouncycastle.cms.SignerInformation;
import org.bouncycastle.cms.jcajce.JcaSimpleSignerInfoVerifierBuilder;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.liquibase.LiquibaseDataSource;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/** VS-034 — une application fait signer l'empreinte de son document, sans le transmettre, par le cachet configuré. */
@SpringBootTest
@AutoConfigureMockMvc
@Import({PostgresDeTest.class, ApplicationsDeTest.class, ReferentielsSql.class})
class EmpreinteApiTest {

    @DynamicPropertySource
    static void cachet(DynamicPropertyRegistry r) throws Exception {
        Path dossier = Files.createTempDirectory("cachet-essai");
        r.add("vinasign.cachet.fournisseur", () -> "pkcs12");
        r.add("vinasign.cachet.magasin", () -> Certificats.magasin(dossier, "RSA", "essai".toCharArray()).toString());
        r.add("vinasign.cachet.mot-de-passe", () -> "essai");
    }

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired ApplicationsDeTest applications;
    @Autowired @LiquibaseDataSource DataSource proprietaire;

    private MockHttpServletResponse signer(Application a, String portee, String sha256) throws Exception {
        return mvc.perform(MockMvcRequestBuilders.post("/api/v1/empreintes/signature")
                .with(jwt().jwt(j -> j.subject(a.clientId()).claim("org", a.organisation().toString()).claim("client_id", a.clientId()))
                        .authorities(new SimpleGrantedAuthority("SCOPE_" + portee)))
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("sha256", sha256)))).andReturn().getResponse();
    }

    @Test
    void le_cms_rendu_se_verifie_avec_le_document_que_vinasign_n_a_jamais_vu() throws Exception {
        Application a = applications.enregistrer("empreintes-signer");
        byte[] document = "Note clinique".getBytes(StandardCharsets.UTF_8);
        String sha = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(document));
        MockHttpServletResponse r = signer(a, "empreintes-signer", sha);
        assertEquals(200, r.getStatus(), r.getContentAsString());
        var corps = json.readTree(r.getContentAsString());
        assertFalse(corps.get("horodatee").asBoolean(), "aucune autorité configurée ici");
        CMSSignedData cms = new CMSSignedData(new CMSProcessableByteArray(document), Base64.getDecoder().decode(corps.get("cms").asText()));
        SignerInformation si = cms.getSignerInfos().getSigners().iterator().next();
        assertTrue(si.verify(new JcaSimpleSignerInfoVerifierBuilder().build((X509CertificateHolder) cms.getCertificates().getMatches(si.getSID())
                .iterator().next())));
        assertEquals(1, new JdbcTemplate(proprietaire).queryForObject(
                "SELECT count(*) FROM journal_audit WHERE tenant_id = ? AND action = 'empreinte-signee' AND cible = ?", Integer.class, a.organisation(), sha));
    }

    @Test
    void sans_la_portee_ou_avec_une_empreinte_mal_formee_c_est_refuse() throws Exception {
        Application a = applications.enregistrer("empreintes-signer");
        String sha = "a".repeat(64);
        assertEquals(403, signer(a, "enveloppes-lire", sha).getStatus());
        assertEquals(400, signer(a, "empreintes-signer", "z".repeat(64)).getStatus());
        assertEquals(400, signer(a, "empreintes-signer", "abc").getStatus());
    }
}
