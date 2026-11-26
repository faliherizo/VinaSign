package ca.vinaitsolution.vinasign.enveloppe;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

import ca.vinaitsolution.vinasign.enveloppe.infrastructure.Pdfs;
import ca.vinaitsolution.vinasign.support.ApplicationsDeTest;
import ca.vinaitsolution.vinasign.support.ApplicationsDeTest.Application;
import ca.vinaitsolution.vinasign.support.InvitationsCapturees;
import ca.vinaitsolution.vinasign.support.PostgresDeTest;
import ca.vinaitsolution.vinasign.support.ReferentielsSql;
import ca.vinaitsolution.vinasign.support.StockageDeTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.time.Instant;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.imageio.ImageIO;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.liquibase.LiquibaseDataSource;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/** VS-050 à VS-052, VS-045 — du document à la dernière signature, sur la vraie pile (PostgreSQL, Azurite, ClamAV). */
@SpringBootTest
@AutoConfigureMockMvc
@Import({PostgresDeTest.class, ApplicationsDeTest.class, ReferentielsSql.class, StockageDeTest.class, InvitationsCapturees.class})
class SignatureApiTest {

    private static final List<String> PORTEES = List.of("enveloppes-creer", "enveloppes-lire", "preuves-lire", "documents-telecharger", "enveloppes-envoyer", "documents-deposer",
            "signataires-gerer");

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired ApplicationsDeTest applications;
    @Autowired InvitationsCapturees invitations;
    @Autowired @LiquibaseDataSource DataSource proprietaire;
    @Autowired ca.vinaitsolution.vinasign.enveloppe.application.FinaliserEnveloppes finalisation;
    @Autowired ca.vinaitsolution.vinasign.enveloppe.application.AncrerJournal ancrage;
    @Autowired ca.vinaitsolution.vinasign.enveloppe.application.port.VersionsDocuments versions;
    @Autowired ca.vinaitsolution.vinasign.commun.application.port.ExecutionOrganisation execution;
    @Autowired ca.vinaitsolution.vinasign.enveloppe.application.RenouvelerArchives archivage;
    @Autowired ca.vinaitsolution.vinasign.enveloppe.application.port.StockageDocuments stockage;
    @Autowired io.micrometer.core.instrument.MeterRegistry metriques;

    /** Cachet d'essai (PKCS#12 généré) : la finalisation scelle le document final (VS-070). */
    @org.springframework.test.context.DynamicPropertySource
    static void cachet(org.springframework.test.context.DynamicPropertyRegistry r) throws Exception {
        java.nio.file.Path dossier = java.nio.file.Files.createTempDirectory("cachet-finalisation");
        r.add("vinasign.cachet.fournisseur", () -> "pkcs12");
        r.add("vinasign.cachet.magasin", () -> ca.vinaitsolution.vinasign.signature.cle.Certificats.magasin(dossier, "RSA", "essai".toCharArray()).toString());
        r.add("vinasign.cachet.mot-de-passe", () -> "essai");
        r.add("vinasign.preuve.ancrage.marge", () -> "PT0S");
    }

    private Application app;
    private byte[] pdf;

    @BeforeEach
    void preparer() {
        app = applications.enregistrer(PORTEES.toArray(String[]::new));
        pdf = Pdfs.pages(2);
    }

    private JwtRequestPostProcessor jeton() {
        return jwt().jwt(j -> j.subject(app.clientId()).claim("org", app.organisation().toString()).claim("client_id", app.clientId()))
                .authorities(PORTEES.stream().<GrantedAuthority>map(p -> new SimpleGrantedAuthority("SCOPE_" + p)).toList());
    }

    private MockHttpServletResponse app(HttpMethod m, String url, Object corps) throws Exception {
        MockHttpServletRequestBuilder r = MockMvcRequestBuilders.request(m, url).with(jeton()).header("Idempotency-Key", UUID.randomUUID().toString());
        if (corps != null) r = r.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(corps));
        return mvc.perform(r).andReturn().getResponse();
    }

    private MockHttpServletResponse signataire(HttpMethod m, String url, String session, Object corps) throws Exception {
        MockHttpServletRequestBuilder r = MockMvcRequestBuilders.request(m, url).header("X-Session-Signataire", session);
        if (corps != null) r = r.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(corps));
        return mvc.perform(r).andReturn().getResponse();
    }

    private JsonNode corps(MockHttpServletResponse r) throws Exception {
        return json.readTree(r.getContentAsString());
    }

    private String code(String referentiel, String code) throws Exception {
        for (JsonNode v : corps(mvc.perform(MockMvcRequestBuilders.get("/api/v1/referentiels/" + referentiel)).andReturn().getResponse()).get("valeurs")) {
            if (v.get("code").asText().equals(code)) return code;
        }
        throw new IllegalStateException(referentiel + " sans " + code);
    }

    /** Enveloppe en mode document, deux signataires dans l'ordre, document de deux pages, champs ; envoyée. Rend id et signataires. */
    private Map<String, String> envoyee() throws Exception {
        String id = corps(app(HttpMethod.POST, "/api/v1/envelopes", Map.of("externalReference", "SIG-" + UUID.randomUUID(),
                "mode", code("mode-enveloppe", "document"), "expiresAt", Instant.now().plusSeconds(86400).toString()))).get("id").asText();
        JsonNode e = corps(app(HttpMethod.PUT, "/api/v1/envelopes/" + id + "/signers", List.of(
                Map.of("nom", "Alice", "courriel", "alice-s@exemple.ca", "langue", code("langue", "fr"), "type", code("type-signataire", "invite"), "ordre", 1),
                Map.of("nom", "Bruno", "courriel", "bruno-s@exemple.ca", "langue", code("langue", "fr"), "type", code("type-signataire", "invite"), "ordre", 2))));
        String alice = e.get("signataires").get(0).get("id").asText();
        String bruno = e.get("signataires").get(1).get("id").asText();
        JsonNode depot = corps(app(HttpMethod.POST, "/api/v1/envelopes/" + id + "/documents", null));
        mvc.perform(MockMvcRequestBuilders.put(URI.create(depot.get("url").asText()).getPath()).contentType("application/pdf").content(pdf));
        app(HttpMethod.POST, "/api/v1/envelopes/" + id + "/documents/" + depot.get("id").asText() + "/confirmation", null);
        MockHttpServletResponse champs = app(HttpMethod.PUT, "/api/v1/envelopes/" + id + "/fields", List.of(
                Map.of("signataire", alice, "type", code("type-champ", "signature"), "libelle", "Signature d'Alice", "page", 2,
                        "x", 0.1, "y", 0.8, "largeur", 0.3, "hauteur", 0.08, "obligatoire", true),
                Map.of("signataire", alice, "type", code("type-champ", "texte"), "libelle", "Titre du poste", "page", 1,
                        "x", 0.1, "y", 0.1, "largeur", 0.4, "hauteur", 0.05, "obligatoire", true),
                Map.of("signataire", bruno, "type", code("type-champ", "date"), "libelle", "Date", "page", 2,
                        "x", 0.6, "y", 0.8, "largeur", 0.2, "hauteur", 0.05, "obligatoire", true)));
        assertEquals(200, champs.getStatus(), champs.getContentAsString());
        assertEquals(200, app(HttpMethod.POST, "/api/v1/envelopes/" + id + "/send", null).getStatus());
        return Map.of("id", id, "alice", alice, "bruno", bruno);
    }

    private String session(String enveloppe, int rang) throws Exception {
        String lien = invitations.pour(enveloppe).get(rang).get("jeton");
        MockHttpServletResponse r = mvc.perform(MockMvcRequestBuilders.post("/api/v1/signataire/ouverture").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("jeton", lien)))).andReturn().getResponse();
        assertEquals(200, r.getStatus(), r.getContentAsString());
        return corps(r).get("jetonSession").asText() + "|" + corps(r).get("versionConsentement").asText() + "|" + lien;
    }

    private static String png() throws Exception {
        BufferedImage image = new BufferedImage(200, 60, BufferedImage.TYPE_INT_ARGB);
        image.getGraphics().drawLine(10, 30, 190, 30);
        ByteArrayOutputStream o = new ByteArrayOutputStream();
        ImageIO.write(image, "png", o);
        return Base64.getEncoder().encodeToString(o.toByteArray());
    }

    @Test
    void du_document_a_la_derniere_signature_dans_l_ordre_avec_champs_et_consentement() throws Exception {
        Map<String, String> e = envoyee();
        String[] alice = session(e.get("id"), 0).split("\\|");
        assertArrayEquals(pdf, signataire(HttpMethod.GET, "/api/v1/signataire/document", alice[0], null).getContentAsByteArray(),
                "le document, empreinte revérifiée");
        JsonNode champs = corps(signataire(HttpMethod.GET, "/api/v1/signataire/champs", alice[0], null));
        assertEquals(2, champs.size(), "seulement les champs d'Alice");
        String champTexte = champs.findValuesAsText("libelle").indexOf("Titre du poste") == 0 ? champs.get(0).get("id").asText() : champs.get(1).get("id").asText();
        String champSignature = champs.get(0).get("id").asText().equals(champTexte) ? champs.get(1).get("id").asText() : champs.get(0).get("id").asText();

        MockHttpServletResponse sansConsentement = signataire(HttpMethod.POST, "/api/v1/signataire/signature", alice[0], Map.of("image", png()));
        assertEquals(403, sansConsentement.getStatus());
        assertTrue(corps(sansConsentement).get("type").asText().endsWith("/consentement-requis"));
        signataire(HttpMethod.POST, "/api/v1/signataire/consentement", alice[0], Map.of("version", alice[1]));

        MockHttpServletResponse incomplet = signataire(HttpMethod.POST, "/api/v1/signataire/signature", alice[0],
                Map.of("image", png(), "valeurs", Map.of(champSignature, "ok")));
        assertEquals(422, incomplet.getStatus());
        assertTrue(corps(incomplet).get("detail").asText().contains("Titre du poste"), "le message dit quoi corriger");

        Map<String, String> valeurs = new HashMap<>(Map.of(champSignature, "signée", champTexte, "Infirmière"));
        assertEquals(204, signataire(HttpMethod.POST, "/api/v1/signataire/signature", alice[0], Map.of("image", png(), "valeurs", valeurs)).getStatus());
        assertEquals(410, mvc.perform(MockMvcRequestBuilders.post("/api/v1/signataire/ouverture").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("jeton", alice[2])))).andReturn().getResponse().getStatus(), "lien rejoué après la signature");
        assertEquals(2, invitations.pour(e.get("id")).size(), "Bruno est invité à son tour");

        String[] bruno = session(e.get("id"), 1).split("\\|");
        signataire(HttpMethod.POST, "/api/v1/signataire/consentement", bruno[0], Map.of("version", bruno[1]));
        JsonNode champsBruno = corps(signataire(HttpMethod.GET, "/api/v1/signataire/champs", bruno[0], null));
        assertEquals(2, champsBruno.size(), "sa date et la zone de signature posée à l'envoi, faute d'en avoir une");
        String champDate = champsBruno.get(0).get("type").asText().equals("date") ? champsBruno.get(0).get("id").asText() : champsBruno.get(1).get("id").asText();
        assertEquals(204, signataire(HttpMethod.POST, "/api/v1/signataire/signature", bruno[0],
                Map.of("nomSaisi", "Bruno Roy", "valeurs", Map.of(champDate, "2026-11-23"))).getStatus(), "alternative au clavier : nom tapé");

        JsonNode enveloppe = corps(app(HttpMethod.GET, "/api/v1/envelopes/" + e.get("id"), null));
        assertEquals(List.of("signe", "signe"), List.of(enveloppe.get("signataires").get(0).get("statut").asText(), enveloppe.get("signataires").get(1).get("statut").asText()));
        assertTrue(enveloppe.get("evenements").findValuesAsText("type").contains("signatures-recueillies"));
        JdbcTemplate proprio = new JdbcTemplate(proprietaire);
        assertEquals(List.of("dessin", "saisie"), proprio.queryForList("SELECT mode FROM signature_signataire WHERE enveloppe_id = ?::uuid ORDER BY signe_le",
                String.class, e.get("id")));
        assertEquals(2, proprio.queryForObject("SELECT count(*) FROM evenement_sortant s JOIN evenement_enveloppe ev ON ev.id = s.evenement_id "
                + "WHERE ev.enveloppe_id = ?::uuid AND s.nom = 'signer.signed'", Integer.class, e.get("id")));
        finaliser(e.get("id"), proprio);
    }

    /**
     * VS-070 : une enveloppe préparée sans aucun champ (portail, sans placement) reçoit à l'envoi une zone de signature
     * par signataire, en bas de la dernière page ; la signature dessinée apparaît sur le document final.
     */
    @Test
    void sans_zone_placee_la_signature_dessinee_apparait_en_bas_de_la_derniere_page() throws Exception {
        String id = corps(app(HttpMethod.POST, "/api/v1/envelopes", Map.of("externalReference", "SANS-ZONE-" + UUID.randomUUID(),
                "mode", code("mode-enveloppe", "document"), "expiresAt", Instant.now().plusSeconds(86400).toString()))).get("id").asText();
        app(HttpMethod.PUT, "/api/v1/envelopes/" + id + "/signers", List.of(Map.of("nom", "Carole Lévesque", "courriel", "carole-s@exemple.ca",
                "langue", code("langue", "fr"), "type", code("type-signataire", "invite"), "ordre", 1)));
        JsonNode depot = corps(app(HttpMethod.POST, "/api/v1/envelopes/" + id + "/documents", null));
        mvc.perform(MockMvcRequestBuilders.put(URI.create(depot.get("url").asText()).getPath()).contentType("application/pdf").content(pdf));
        app(HttpMethod.POST, "/api/v1/envelopes/" + id + "/documents/" + depot.get("id").asText() + "/confirmation", null);
        String carole0 = corps(app(HttpMethod.GET, "/api/v1/envelopes/" + id, null)).get("signataires").get(0).get("id").asText();
        assertEquals(200, app(HttpMethod.PUT, "/api/v1/envelopes/" + id + "/fields", List.of(Map.of("signataire", carole0, "type",
                code("type-champ", "initiales"), "libelle", "Initiales", "page", 1, "x", 0.8, "y", 0.9, "largeur", 0.1, "hauteur", 0.05,
                "obligatoire", true))).getStatus());
        // Le champ d'initiales ne tient pas lieu de signature : la zone de signature est tout de même posée.
        assertEquals(200, app(HttpMethod.POST, "/api/v1/envelopes/" + id + "/send", null).getStatus());
        assertTrue(corps(app(HttpMethod.GET, "/api/v1/envelopes/" + id, null)).get("evenements").findValuesAsText("type")
                .contains("zones-signature-par-defaut"), "zone posée et consignée");

        String[] carole = session(id, 0).split("\\|");
        JsonNode champs = corps(signataire(HttpMethod.GET, "/api/v1/signataire/champs", carole[0], null));
        assertEquals(2, champs.size());
        JsonNode zone = champs.get("signature".equals(champs.get(0).get("type").asText()) ? 0 : 1);
        assertEquals("signature", zone.get("type").asText());
        assertEquals(2, zone.get("page").asInt(), "dernière page du document");
        assertTrue(zone.get("libelle").asText().endsWith("— Carole Lévesque"), zone.get("libelle").asText());
        signataire(HttpMethod.POST, "/api/v1/signataire/consentement", carole[0], Map.of("version", carole[1]));
        assertEquals(204, signataire(HttpMethod.POST, "/api/v1/signataire/signature", carole[0], Map.of("image", png())).getStatus());

        assertTrue(finalisation.executer() >= 1);
        byte[] finale = app(HttpMethod.GET, "/api/v1/envelopes/" + id + "/documents/final", null).getContentAsByteArray();
        try (org.apache.pdfbox.pdmodel.PDDocument doc = org.apache.pdfbox.pdmodel.PDDocument.load(finale)) {
            org.apache.pdfbox.pdmodel.PDResources ressources = doc.getPage(1).getResources();
            boolean image = false;
            for (org.apache.pdfbox.cos.COSName nom : ressources.getXObjectNames()) {
                image |= ressources.getXObject(nom) instanceof org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
            }
            org.apache.pdfbox.text.PDFTextStripper premiere = new org.apache.pdfbox.text.PDFTextStripper();
            premiere.setEndPage(1);
            assertTrue(premiere.getText(doc).contains("CL"), "initiales apposées où elles ont été placées : " + premiere.getText(doc));
            assertTrue(image, "l'image de la signature est apposée sur la dernière page");
        }
        assertTrue(corps(mvc.perform(MockMvcRequestBuilders.post("/api/v1/verifications").contentType("application/pdf").content(finale))
                .andReturn().getResponse()).get("integre").asBoolean());
    }

    /** Les zones placées par l'expéditeur sont gardées : aucun signataire qui a la sienne n'en reçoit une seconde. */
    @Test
    void une_zone_deja_placee_n_est_pas_doublee() throws Exception {
        Map<String, String> e = envoyee();
        JsonNode places = corps(app(HttpMethod.GET, "/api/v1/envelopes/" + e.get("id") + "/fields", null));
        assertEquals(4, places.size(), "les trois champs placés et la zone de Bruno posée à l'envoi, relus par l'application");
        assertEquals(404, app(HttpMethod.GET, "/api/v1/envelopes/" + UUID.randomUUID() + "/fields", null).getStatus());
        String[] alice = session(e.get("id"), 0).split("\\|");
        JsonNode champs = corps(signataire(HttpMethod.GET, "/api/v1/signataire/champs", alice[0], null));
        assertEquals(1, champs.findValuesAsText("type").stream().filter("signature"::equals).count(), "la zone d'Alice, sans doublon");
    }

    /** VS-070 : document final composé, scellé, immuable ; complétée ensuite ; une reprise ne scelle pas deux fois. */
    private void finaliser(String id, JdbcTemplate proprio) throws Exception {
        assertEquals("version-introuvable", type(app(HttpMethod.GET, "/api/v1/envelopes/" + id + "/documents/final", null)), "rien avant la finalisation");
        assertTrue(finalisation.executer() >= 1);
        assertEquals("terminee", corps(app(HttpMethod.GET, "/api/v1/envelopes/" + id, null)).get("statut").asText());
        byte[] finale = app(HttpMethod.GET, "/api/v1/envelopes/" + id + "/documents/final", null).getContentAsByteArray();
        try (org.apache.pdfbox.pdmodel.PDDocument doc = org.apache.pdfbox.pdmodel.PDDocument.load(finale)) {
            assertEquals(1, doc.getSignatureDictionaries().size(), "un seul scellement");
            String texte = new org.apache.pdfbox.text.PDFTextStripper().getText(doc);
            assertTrue(texte.contains("Infirmière") && texte.contains("2026-11-23"), "valeurs apposées : " + texte);
            assertTrue(texte.contains("Bruno Roy"), "le nom tapé de Bruno apparaît dans la zone posée à l'envoi : " + texte);
        }
        JsonNode rapport = corps(mvc.perform(MockMvcRequestBuilders.post("/api/v1/verifications").contentType("application/pdf").content(finale))
                .andReturn().getResponse());
        assertTrue(rapport.get("integre").asBoolean(), rapport.toString());
        assertEquals(1, proprio.queryForObject("SELECT count(*) FROM evenement_sortant s JOIN evenement_enveloppe ev ON ev.id = s.evenement_id "
                + "WHERE ev.enveloppe_id = ?::uuid AND s.nom = 'envelope.completed'", Integer.class, id), "webhook envelope.completed");
        assertEquals(List.of("alice-s@exemple.ca", "bruno-s@exemple.ca"), proprio.queryForList("SELECT destinataire FROM courriel_sortant "
                + "WHERE enveloppe_id = ?::uuid AND sujet LIKE 'Signature terminée%' ORDER BY destinataire", String.class, id), "confirmation à chaque signataire");

        assertEquals(0, finalisation.executer(), "rien de plus à finaliser");
        proprio.update("UPDATE enveloppe SET statut = 'envoyee' WHERE id = ?::uuid", id);
        assertTrue(finalisation.executer() >= 1, "reprise après une interruption entre la version finale et la complétion");
        assertEquals(1, proprio.queryForObject("SELECT count(*) FROM version_document WHERE enveloppe_id = ?::uuid AND nature = 'final'", Integer.class, id));
        assertArrayEquals(finale, app(HttpMethod.GET, "/api/v1/envelopes/" + id + "/documents/final", null).getContentAsByteArray(),
                "la reprise réutilise la version finale : aucun second scellement");
        certificatEtDossier(id, finale);
        UUID organisation = app.organisation();
        assertThrows(ca.vinaitsolution.vinasign.enveloppe.domain.VersionImmuable.class, () -> execution.pour(
                new ca.vinaitsolution.vinasign.commun.domain.IdentifiantOrganisation(organisation), "plateforme:essai", () -> {
                    versions.ajouter(new ca.vinaitsolution.vinasign.enveloppe.domain.VersionDocument(UUID.randomUUID(),
                            new ca.vinaitsolution.vinasign.enveloppe.domain.IdentifiantEnveloppe(UUID.fromString(id)), "final", "autre", "0".repeat(64), 1, 1,
                            java.time.Instant.now()));
                    return null;
                }), "une seule version finale par enveloppe, même par une seconde réplique");
        archiver(id, proprio);
    }

    /** VS-132 : le final est évalué (sans autorité d'horodatage ici : aucune échéance) ; un renouvellement impossible est signalé. */
    private void archiver(String id, JdbcTemplate proprio) throws Exception {
        String compte = "SELECT count(*) FROM archive_document a JOIN version_document v ON v.enveloppe_id = a.enveloppe_id AND v.nature = 'final' "
                + "AND v.cle_objet = a.cle_objet AND v.sha256 = a.sha256 WHERE a.enveloppe_id = ?::uuid AND a.echeance IS NULL";
        for (int i = 0; i < 50 && proprio.queryForObject(compte, Integer.class, id) == 0; i++) {
            assertEquals(0, archivage.executer(), "PAdES-B-B : rien à renouveler");
        }
        assertEquals(1, proprio.queryForObject(compte, Integer.class, id), "le final devient le premier état archivé, sans échéance");
        assertEquals(0, archivage.executer());
        assertEquals(1, proprio.queryForObject("SELECT count(*) FROM archive_document WHERE enveloppe_id = ?::uuid", Integer.class, id), "évalué une fois");
        proprio.update("UPDATE archive_document SET echeance = now() WHERE enveloppe_id = ?::uuid", id);
        double avant = metriques.counter("vinasign.archivage.echecs").count();
        archivage.executer();
        assertTrue(metriques.counter("vinasign.archivage.echecs").count() > avant, "renouvellement impossible sans autorité : alerte");
        assertEquals(1, proprio.queryForObject("SELECT count(*) FROM archive_document WHERE enveloppe_id = ?::uuid", Integer.class, id), "rien de conservé");
        // Un état archivé distinct du final, conservé sous sa propre clé, rejoint le dossier de preuve.
        byte[] archive = "archive renouvelée".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        String cle = "essais/archive-" + UUID.randomUUID() + ".pdf";
        stockage.ecrire(cle, archive);
        assertEquals(1, proprio.update("INSERT INTO archive_document (id, tenant_id, enveloppe_id, cle_objet, sha256, taille, echeance, cree_le) "
                + "SELECT gen_random_uuid(), e.tenant_id, e.id, ?, ?, ?, now() + interval '10 years', "
                + "(SELECT max(a.cree_le) + interval '1 second' FROM archive_document a WHERE a.enveloppe_id = e.id) FROM enveloppe e WHERE e.id = ?::uuid",
                cle, ca.vinaitsolution.vinasign.commun.domain.Condensats.sha256Hex(archive), archive.length, id));
        assertTrue(entrees(id).contains("documents/archive.pdf"), "dernier état archivé joint au dossier");
    }

    private List<String> entrees(String id) {
        List<String> noms = new java.util.ArrayList<>();
        try (java.util.zip.ZipInputStream zip = new java.util.zip.ZipInputStream(new java.io.ByteArrayInputStream(
                app(HttpMethod.GET, "/api/v1/envelopes/" + id + "/preuve/dossier", null).getContentAsByteArray()))) {
            for (java.util.zip.ZipEntry entree; (entree = zip.getNextEntry()) != null; ) {
                noms.add(entree.getName());
            }
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        return noms;
    }

    /** VS-072, VS-073 : certificat PDF/A-2b scellé ; dossier de preuve au manifeste signé, vérifiable hors de VinaSign. */
    private void certificatEtDossier(String id, byte[] finale) throws Exception {
        MockHttpServletResponse r = app(HttpMethod.GET, "/api/v1/envelopes/" + id + "/preuve/certificat", null);
        assertEquals(200, r.getStatus(), r.getContentAsString());
        byte[] certificat = r.getContentAsByteArray();
        try (org.apache.pdfbox.pdmodel.PDDocument doc = org.apache.pdfbox.pdmodel.PDDocument.load(certificat)) {
            // La mise en page coupe les longues valeurs (empreintes) : on compare le texte sans ses blancs.
            String texte = new org.apache.pdfbox.text.PDFTextStripper().getText(doc).replaceAll("\\s+", "");
            for (String attendu : List.of("Certificatderéalisation", "Alice", "Bruno", ca.vinaitsolution.vinasign.enveloppe.domain.ChaineScellee.sha256(finale),
                    "Chaîned'empreintesintacte", "Enveloppecomplétée")) {
                assertTrue(texte.contains(attendu), attendu + " absent de : " + texte);
            }
            assertEquals(1, doc.getSignatureDictionaries().size(), "scellé");
        }
        String rapport = ca.vinaitsolution.vinasign.support.StockageDeTest.rapportPdfA2b(certificat);
        assertTrue(ca.vinaitsolution.vinasign.support.StockageDeTest.conformePdfA2b(rapport), rapport.substring(0, Math.min(800, rapport.length())));
        assertTrue(corps(mvc.perform(MockMvcRequestBuilders.post("/api/v1/verifications").contentType("application/pdf").content(certificat))
                .andReturn().getResponse()).get("integre").asBoolean(), "certificat vérifiable");

        ancrage.executer();
        Map<String, byte[]> fichiers = new java.util.LinkedHashMap<>();
        try (java.util.zip.ZipInputStream zip = new java.util.zip.ZipInputStream(new java.io.ByteArrayInputStream(
                app(HttpMethod.GET, "/api/v1/envelopes/" + id + "/preuve/dossier", null).getContentAsByteArray()))) {
            for (java.util.zip.ZipEntry entree; (entree = zip.getNextEntry()) != null; ) {
                fichiers.put(entree.getName(), zip.readAllBytes());
            }
        }
        assertTrue(fichiers.keySet().containsAll(List.of("documents/original.pdf", "documents/final.pdf", "certificat.pdf", "journal.json",
                "LISEZMOI.txt", "manifeste.json", "manifeste.p7s")), fichiers.keySet().toString());
        assertArrayEquals(finale, fichiers.get("documents/final.pdf"));
        JsonNode manifeste = json.readTree(fichiers.get("manifeste.json"));
        manifeste.fields().forEachRemaining(f -> assertEquals(ca.vinaitsolution.vinasign.enveloppe.domain.ChaineScellee.sha256(fichiers.get(f.getKey())),
                f.getValue().asText(), f.getKey()));
        org.bouncycastle.cms.CMSSignedData cms = new org.bouncycastle.cms.CMSSignedData(
                new org.bouncycastle.cms.CMSProcessableByteArray(fichiers.get("manifeste.json")), fichiers.get("manifeste.p7s"));
        org.bouncycastle.cms.SignerInformation signataire = cms.getSignerInfos().getSigners().iterator().next();
        @SuppressWarnings("unchecked")
        org.bouncycastle.cert.X509CertificateHolder certificatCachet = (org.bouncycastle.cert.X509CertificateHolder)
                cms.getCertificates().getMatches(signataire.getSID()).iterator().next();
        assertTrue(signataire.verify(new org.bouncycastle.cms.jcajce.JcaSimpleSignerInfoVerifierBuilder().build(certificatCachet)),
                "manifeste signé par le cachet, vérifiable hors de VinaSign");
        assertTrue(new String(fichiers.get("LISEZMOI.txt"), java.nio.charset.StandardCharsets.UTF_8).contains("openssl cms -verify"));
        assertTrue(fichiers.containsKey("ancrage.json") && fichiers.containsKey("ancrage.p7s"), "le journal est couvert par un ancrage signé");
        assertTrue(json.readTree(fichiers.get("ancrage.json")).get("evenements").asInt() >= 1);
        JsonNode journal = json.readTree(fichiers.get("journal.json"));
        java.util.List<ca.vinaitsolution.vinasign.enveloppe.domain.ChaineScellee.Maillon> maillons = new java.util.ArrayList<>();
        journal.get("maillons").forEach(m -> maillons.add(new ca.vinaitsolution.vinasign.enveloppe.domain.ChaineScellee.Maillon(m.get("sequence").asLong(),
                m.get("type").asText(), m.get("acteur").asText(), m.get("microsecondes").asLong(), m.get("donnees").asText(),
                m.get("empreintePrecedente").asText(), m.get("empreinte").asText())));
        assertTrue(ca.vinaitsolution.vinasign.enveloppe.domain.ChaineScellee.verifier(UUID.fromString(id), maillons).integre(),
                "le journal exporté se revérifie hors de la plateforme");
        assertTrue(corps(app(HttpMethod.GET, "/api/v1/envelopes/" + id, null)).get("evenements").findValuesAsText("type")
                .containsAll(List.of("certificat-produit", "preuve-exportee")), "accès consignés");
    }

    @Test
    void un_refus_termine_l_enveloppe_sans_inviter_la_suite() throws Exception {
        Map<String, String> e = envoyee();
        String[] alice = session(e.get("id"), 0).split("\\|");
        assertEquals(204, signataire(HttpMethod.POST, "/api/v1/signataire/refus", alice[0], Map.of("motif", code("motif-refus", "contenu-incorrect")))
                .getStatus(), "aucun consentement requis pour refuser");
        assertEquals("refusee", corps(app(HttpMethod.GET, "/api/v1/envelopes/" + e.get("id"), null)).get("statut").asText());
        assertEquals(1, new org.springframework.jdbc.core.JdbcTemplate(proprietaire).queryForObject("SELECT count(*) FROM evenement_sortant s "
                + "JOIN evenement_enveloppe ev ON ev.id = s.evenement_id WHERE ev.enveloppe_id = ?::uuid AND s.nom = 'signer.declined'", Integer.class, e.get("id")),
                "webhook signer.declined");
        assertEquals(1, invitations.pour(e.get("id")).size(), "Bruno n'est jamais invité");
        assertEquals(410, mvc.perform(MockMvcRequestBuilders.post("/api/v1/signataire/ouverture").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("jeton", alice[2])))).andReturn().getResponse().getStatus());
    }

    @Test
    void les_signatures_et_les_champs_mal_formes_sont_refuses() throws Exception {
        Map<String, String> e = envoyee();
        String[] alice = session(e.get("id"), 0).split("\\|");
        signataire(HttpMethod.POST, "/api/v1/signataire/consentement", alice[0], Map.of("version", alice[1]));
        assertEquals("signature-requise", type(signataire(HttpMethod.POST, "/api/v1/signataire/signature", alice[0], Map.of())));
        assertEquals("signature-requise", type(signataire(HttpMethod.POST, "/api/v1/signataire/signature", alice[0], Map.of("image", png(), "nomSaisi", "A"))));
        assertEquals("signature-image", type(signataire(HttpMethod.POST, "/api/v1/signataire/signature", alice[0],
                Map.of("image", Base64.getEncoder().encodeToString("pas une image".getBytes())))));
        assertEquals(400, signataire(HttpMethod.POST, "/api/v1/signataire/signature", alice[0], Map.of("image", "%%%")).getStatus());
        assertEquals("champ-etranger", type(signataire(HttpMethod.POST, "/api/v1/signataire/signature", alice[0],
                Map.of("nomSaisi", "Alice", "valeurs", Map.of(UUID.randomUUID().toString(), "x")))));
        assertEquals(409, app(HttpMethod.PUT, "/api/v1/envelopes/" + e.get("id") + "/fields", List.of()).getStatus(), "champs figés après l'envoi");
        assertEquals(401, mvc.perform(MockMvcRequestBuilders.get("/api/v1/signataire/document")).andReturn().getResponse().getStatus());
    }

    @Test
    void les_champs_visent_un_signataire_une_page_et_un_type_existants() throws Exception {
        String id = corps(app(HttpMethod.POST, "/api/v1/envelopes", Map.of("externalReference", "CH-" + UUID.randomUUID(),
                "mode", code("mode-enveloppe", "document"), "expiresAt", Instant.now().plusSeconds(86400).toString()))).get("id").asText();
        String alice = corps(app(HttpMethod.PUT, "/api/v1/envelopes/" + id + "/signers", List.of(Map.of("nom", "Alice", "courriel", "a@exemple.ca",
                "langue", code("langue", "fr"), "type", code("type-signataire", "invite"), "ordre", 1)))).get("signataires").get(0).get("id").asText();
        JsonNode depot = corps(app(HttpMethod.POST, "/api/v1/envelopes/" + id + "/documents", null));
        mvc.perform(MockMvcRequestBuilders.put(URI.create(depot.get("url").asText()).getPath()).contentType("application/pdf").content(pdf));
        app(HttpMethod.POST, "/api/v1/envelopes/" + id + "/documents/" + depot.get("id").asText() + "/confirmation", null);
        Map<String, Object> base = Map.of("signataire", alice, "type", code("type-champ", "texte"), "libelle", "Nom", "page", 1,
                "x", 0.1, "y", 0.1, "largeur", 0.2, "hauteur", 0.05, "obligatoire", false);
        assertEquals("page-inexistante", type(app(HttpMethod.PUT, "/api/v1/envelopes/" + id + "/fields", List.of(avec(base, "page", 3)))));
        assertEquals("signataire-inconnu", type(app(HttpMethod.PUT, "/api/v1/envelopes/" + id + "/fields",
                List.of(avec(base, "signataire", UUID.randomUUID().toString())))));
        assertEquals("valeur-referentiel-inconnue", type(app(HttpMethod.PUT, "/api/v1/envelopes/" + id + "/fields", List.of(avec(base, "type", "inconnu")))));
        assertEquals(400, app(HttpMethod.PUT, "/api/v1/envelopes/" + id + "/fields", List.of(avec(base, "x", 0.95))).getStatus(), "hors de la page");
    }

    @Test
    void les_champs_se_placent_par_ancres_textuelles_du_document() throws Exception {
        String id = corps(app(HttpMethod.POST, "/api/v1/envelopes", Map.of("externalReference", "ANC-" + UUID.randomUUID(),
                "mode", code("mode-enveloppe", "document"), "expiresAt", Instant.now().plusSeconds(86400).toString()))).get("id").asText();
        String alice = corps(app(HttpMethod.PUT, "/api/v1/envelopes/" + id + "/signers", List.of(
                Map.of("nom", "Alice", "courriel", "a@exemple.ca", "langue", code("langue", "fr"), "type", code("type-signataire", "invite"), "ordre", 1),
                Map.of("nom", "Bruno", "courriel", "b@exemple.ca", "langue", code("langue", "fr"), "type", code("type-signataire", "invite"), "ordre", 2))))
                .get("signataires").get(0).get("id").asText();
        Map<String, Object> ancre = Map.of("ancre", "{{signature:1}}", "ordre", 1, "type", code("type-champ", "signature"), "libelle", "Signature d'Alice",
                "largeur", 0.3, "hauteur", 0.06, "obligatoire", true);
        assertEquals("document-absent", type(app(HttpMethod.PUT, "/api/v1/envelopes/" + id + "/fields/anchors", List.of(ancre))));
        JsonNode depot = corps(app(HttpMethod.POST, "/api/v1/envelopes/" + id + "/documents", null));
        mvc.perform(MockMvcRequestBuilders.put(URI.create(depot.get("url").asText()).getPath()).contentType("application/pdf")
                .content(ca.vinaitsolution.vinasign.enveloppe.infrastructure.Pdfs.avecTextes(new Object[] {"Lu et approuvé {{signature:1}}", 72, 150},
                        new Object[] {"{{signature:1}}", 72, 150})));
        app(HttpMethod.POST, "/api/v1/envelopes/" + id + "/documents/" + depot.get("id").asText() + "/confirmation", null);

        JsonNode champs = corps(app(HttpMethod.PUT, "/api/v1/envelopes/" + id + "/fields/anchors", List.of(ancre)));
        assertEquals(2, champs.size(), "une occurrence par page");
        assertEquals(List.of(1, 2), List.of(champs.get(0).get("page").asInt(), champs.get(1).get("page").asInt()));
        assertEquals(alice, champs.get(0).get("signataire").asText());
        assertTrue(champs.get(1).get("y").asDouble() > 0.7 && champs.get(1).get("x").asDouble() < 0.15);
        assertEquals("ancre-absente", type(app(HttpMethod.PUT, "/api/v1/envelopes/" + id + "/fields/anchors", List.of(avec(ancre, "ancre", "{{date:2}}")))));
        assertTrue(corps(app(HttpMethod.PUT, "/api/v1/envelopes/" + id + "/fields/anchors", List.of(avec(ancre, "ancre", "{{date:2}}")))).get("detail")
                .asText().contains("{{date:2}}"), "le message nomme l'ancre");
        assertEquals("ordre-ancre", type(app(HttpMethod.PUT, "/api/v1/envelopes/" + id + "/fields/anchors", List.of(avec(ancre, "ordre", 3)))));
        assertEquals(400, app(HttpMethod.PUT, "/api/v1/envelopes/" + id + "/fields/anchors", List.of(avec(ancre, "ancre", "{{a b}}"))).getStatus(),
                "une ancre ne contient pas de blanc");
    }

    private static Map<String, Object> avec(Map<String, Object> base, String cle, Object valeur) {
        Map<String, Object> m = new HashMap<>(base);
        m.put(cle, valeur);
        return m;
    }

    private String type(MockHttpServletResponse r) throws Exception {
        return corps(r).path("type").asText().replaceAll(".*/", "");
    }
}
