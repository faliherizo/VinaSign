package ca.vinaitsolution.vinasign.enveloppe;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

import ca.vinaitsolution.vinasign.commun.domain.Libelles;
import ca.vinaitsolution.vinasign.enveloppe.application.ExpirerEnveloppes;
import ca.vinaitsolution.vinasign.enveloppe.infrastructure.Pdfs;
import ca.vinaitsolution.vinasign.referentiel.domain.CodeReferentiel;
import ca.vinaitsolution.vinasign.referentiel.domain.ValeurReferentiel;
import ca.vinaitsolution.vinasign.support.ApplicationsDeTest;
import ca.vinaitsolution.vinasign.support.ApplicationsDeTest.Application;
import ca.vinaitsolution.vinasign.support.PostgresDeTest;
import ca.vinaitsolution.vinasign.support.ReferentielsSql;
import ca.vinaitsolution.vinasign.support.StockageDeTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.net.URI;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/**
 * VS-020 à VS-025 — enveloppes et documents, de bout en bout : PostgreSQL, Azurite et ClamAV réels. Les codes de
 * référentiel (mode, langue, type de signataire) sont découverts par l'API, jamais cités.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({PostgresDeTest.class, ApplicationsDeTest.class, ReferentielsSql.class, StockageDeTest.class})
class EnveloppeApiTest {

    private static final List<String> PORTEES = List.of("enveloppes-creer", "enveloppes-lire", "enveloppes-envoyer", "documents-deposer",
            "signataires-gerer");

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired ApplicationsDeTest applications;
    @Autowired ReferentielsSql referentiels;
    @Autowired ExpirerEnveloppes expiration;
    @Autowired @LiquibaseDataSource DataSource proprietaire;

    private Application app;
    private String document;
    private String empreinte;
    private Map<String, String> typeParExigence;

    @BeforeEach
    void preparer() throws Exception {
        app = applications.enregistrer(PORTEES.toArray(String[]::new));
        document = valeur("mode-enveloppe", v -> v.get("code").asText().equals("document"));
        empreinte = valeur("mode-enveloppe", v -> !v.get("code").asText().equals("document"));
        typeParExigence = Map.of(
                "courriel", valeur("type-signataire", v -> v.path("attributs").path("exigeCourriel").asText().equals("true")),
                "sujet", valeur("type-signataire", v -> v.path("attributs").path("exigeSujetExterne").asText().equals("true")));
    }

    /** Premier code d'un référentiel satisfaisant le critère, lu par l'API publique. */
    private String valeur(String referentiel, java.util.function.Predicate<JsonNode> critere) throws Exception {
        for (JsonNode v : corps(mvc.perform(MockMvcRequestBuilders.get("/api/v1/referentiels/" + referentiel)).andReturn().getResponse()).get("valeurs")) {
            if (critere.test(v)) return v.get("code").asText();
        }
        throw new IllegalStateException("Aucune valeur de " + referentiel + " ne convient.");
    }

    private String langue() throws Exception {
        return valeur("langue", v -> true);
    }

    private JwtRequestPostProcessor jeton(Application a) {
        return jwt().jwt(j -> j.subject(a.clientId()).claim("org", a.organisation().toString()).claim("client_id", a.clientId()))
                .authorities(PORTEES.stream().<org.springframework.security.core.GrantedAuthority>map(p -> new SimpleGrantedAuthority("SCOPE_" + p)).toList());
    }

    private MockHttpServletResponse appel(HttpMethod m, String url, Object corps, String cle) throws Exception {
        return appel(app, m, url, corps, cle);
    }

    private MockHttpServletResponse appel(Application a, HttpMethod m, String url, Object corps, String cle) throws Exception {
        MockHttpServletRequestBuilder r = MockMvcRequestBuilders.request(m, url).with(jeton(a));
        if (corps != null) r = r.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(corps));
        if (cle != null) r = r.header("Idempotency-Key", cle);
        return mvc.perform(r).andReturn().getResponse();
    }

    private JsonNode corps(MockHttpServletResponse r) throws Exception {
        return json.readTree(r.getContentAsString());
    }

    private ObjectNode nouvelle(String mode, Instant echeance) {
        return json.createObjectNode().put("externalReference", "REF-" + UUID.randomUUID()).put("mode", mode).put("expiresAt", echeance.toString());
    }

    private String creer(String mode) throws Exception {
        MockHttpServletResponse r = appel(HttpMethod.POST, "/api/v1/envelopes", nouvelle(mode, Instant.now().plusSeconds(86400)), UUID.randomUUID().toString());
        assertEquals(201, r.getStatus(), r.getContentAsString());
        return corps(r).get("id").asText();
    }

    private ArrayNode signataires(Object... nomsEtOrdres) throws Exception {
        ArrayNode liste = json.createArrayNode();
        for (int i = 0; i < nomsEtOrdres.length; i += 2) {
            liste.addObject().put("nom", (String) nomsEtOrdres[i]).put("courriel", "s" + i + "@exemple.ca").put("langue", langue())
                    .put("type", typeParExigence.get("courriel")).put("ordre", (Integer) nomsEtOrdres[i + 1]);
        }
        return liste;
    }

    private String deposer(String id, byte[] contenu) throws Exception {
        MockHttpServletResponse demande = appel(HttpMethod.POST, "/api/v1/envelopes/" + id + "/documents", null, null);
        assertEquals(201, demande.getStatus(), demande.getContentAsString());
        JsonNode depot = corps(demande);
        assertTrue(Instant.parse(depot.get("expireLe").asText()).isBefore(Instant.now().plusSeconds(301)), "URL de 5 minutes au plus");
        URI url = URI.create(depot.get("url").asText());
        MockHttpServletResponse recu = mvc.perform(MockMvcRequestBuilders.put(url.getPath()).contentType("application/octet-stream").content(contenu))
                .andReturn().getResponse();
        assertEquals(204, recu.getStatus(), recu.getContentAsString());
        assertEquals(422, mvc.perform(MockMvcRequestBuilders.put(url.getPath()).contentType("application/octet-stream").content(contenu))
                .andReturn().getResponse().getStatus(), "une URL ne sert qu'à un objet");
        return depot.get("id").asText();
    }

    private List<String> sortants(String id) {
        return new JdbcTemplate(proprietaire).queryForList("""
                SELECT s.nom FROM evenement_sortant s JOIN evenement_enveloppe e ON e.id = s.evenement_id
                WHERE e.enveloppe_id = ?::uuid ORDER BY s.cree_le""", String.class, id);
    }

    @Test
    void parcours_complet_creation_signataires_depot_controle_et_envoi() throws Exception {
        String id = creer(document);
        assertEquals(200, appel(HttpMethod.PUT, "/api/v1/envelopes/" + id + "/signers", signataires("Alice", 1, "Bruno", 2, "Chloé", 2), null).getStatus());
        byte[] pdf = Pdfs.pages(2);
        String depot = deposer(id, pdf);
        MockHttpServletResponse confirme = appel(HttpMethod.POST, "/api/v1/envelopes/" + id + "/documents/" + depot + "/confirmation", null, null);
        assertEquals(200, confirme.getStatus(), confirme.getContentAsString());
        JsonNode version = corps(confirme).get("documents").get(0);
        assertEquals("original", version.get("nature").asText());
        assertEquals(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(pdf)), version.get("sha256").asText());
        assertEquals(2, version.get("pages").asInt());
        assertEquals("depot-non-recu", type(appel(HttpMethod.POST, "/api/v1/envelopes/" + id + "/documents/" + depot + "/confirmation", null, null)),
                "un dépôt confirmé ne se confirme pas deux fois");

        MockHttpServletResponse envoi = appel(HttpMethod.POST, "/api/v1/envelopes/" + id + "/send", null, null);
        assertEquals(200, envoi.getStatus(), envoi.getContentAsString());
        JsonNode e = corps(appel(HttpMethod.GET, "/api/v1/envelopes/" + id, null, null));
        assertEquals("envoyee", e.get("statut").asText());
        assertEquals(List.of(true, false, false), List.of(e.get("signataires").get(0).get("peutAgir").asBoolean(),
                e.get("signataires").get(1).get("peutAgir").asBoolean(), e.get("signataires").get(2).get("peutAgir").asBoolean()),
                "ordre 1 d'abord ; les ordres 2 ensuite, en parallèle");
        assertFalse(e.toString().contains("@exemple.ca"), "aucun courriel renvoyé");
        List<String> types = e.get("evenements").findValuesAsText("type");
        assertEquals(List.of("enveloppe-creee", "signataires-definis", "document-depose", "zones-signature-par-defaut", "enveloppe-envoyee"), types);
        assertEquals(List.of("envelope.sent"), sortants(id), "seul le type marqué webhook entre dans la boîte d'envoi");
        assertEquals(409, appel(HttpMethod.PUT, "/api/v1/envelopes/" + id + "/signers", signataires("Zoé", 1), null).getStatus(),
                "les signataires ne changent plus après l'envoi");
    }

    @Test
    void un_document_word_depose_est_converti_en_pdf_a_puis_controle() throws Exception {
        String id = creer(document);
        String depot = deposer(id, ca.vinaitsolution.vinasign.enveloppe.infrastructure.Bureautique.docx("Contrat de service"));
        MockHttpServletResponse r = appel(HttpMethod.POST, "/api/v1/envelopes/" + id + "/documents/" + depot + "/confirmation", null, null);
        assertEquals(200, r.getStatus(), r.getContentAsString());
        assertEquals(1, corps(r).get("documents").get(0).get("pages").asInt(), "le PDF/A converti est retenu");
    }

    @Test
    void idempotence_meme_cle_meme_reponse_autre_corps_refuse_reference_unique() throws Exception {
        ObjectNode demande = nouvelle(empreinte, Instant.now().plusSeconds(3600));
        String cle = UUID.randomUUID().toString();
        MockHttpServletResponse premiere = appel(HttpMethod.POST, "/api/v1/envelopes", demande, cle);
        MockHttpServletResponse seconde = appel(HttpMethod.POST, "/api/v1/envelopes", demande, cle);
        assertEquals(201, seconde.getStatus());
        assertEquals("true", seconde.getHeader("Idempotent-Replayed"));
        assertEquals(corps(premiere), corps(seconde), "même clé, même corps : même réponse, aucune seconde enveloppe");
        // Requête concurrente : la première n'a pas encore mémorisé sa réponse.
        new JdbcTemplate(proprietaire).update("UPDATE requete_idempotente SET statut = NULL, reponse = NULL WHERE cle = ?", cle);
        MockHttpServletResponse enCours = appel(HttpMethod.POST, "/api/v1/envelopes", demande, cle);
        assertEquals(409, enCours.getStatus());
        assertTrue(corps(enCours).get("type").asText().endsWith("/requete-en-cours"));
        ObjectNode autre = demande.deepCopy().put("externalReference", "AUTRE-" + UUID.randomUUID());
        MockHttpServletResponse refus = appel(HttpMethod.POST, "/api/v1/envelopes", autre, cle);
        assertEquals(422, refus.getStatus());
        assertTrue(corps(refus).get("type").asText().endsWith("/cle-idempotence-reutilisee"));
        MockHttpServletResponse doublon = appel(HttpMethod.POST, "/api/v1/envelopes", demande, UUID.randomUUID().toString());
        assertEquals(409, doublon.getStatus(), "même référence externe pour la même application");
        assertTrue(corps(doublon).get("type").asText().endsWith("/reference-externe-deja-utilisee"));
        assertEquals(400, appel(HttpMethod.POST, "/api/v1/envelopes", nouvelle(empreinte, Instant.now().plusSeconds(60)), null).getStatus(),
                "clé d'idempotence obligatoire");
        Application autreApp = applications.enregistrer(app.organisation(), PORTEES.toArray(String[]::new));
        assertEquals(201, appel(autreApp, HttpMethod.POST, "/api/v1/envelopes", demande, UUID.randomUUID().toString()).getStatus(),
                "la référence est unique par application, pas par organisation");
    }

    @Test
    void les_documents_non_conformes_sont_refuses_explicitement() throws Exception {
        String id = creer(document);
        String texte = deposer(id, "Ceci n'est pas un PDF".getBytes());
        assertEquals("document-type", type(appel(HttpMethod.POST, "/api/v1/envelopes/" + id + "/documents/" + texte + "/confirmation", null, null)));
        String chiffre = deposer(id, Pdfs.chiffre());
        assertEquals("document-chiffre", type(appel(HttpMethod.POST, "/api/v1/envelopes/" + id + "/documents/" + chiffre + "/confirmation", null, null)));
        String tronque = deposer(id, "%PDF-1.7 tronqué".getBytes());
        assertEquals("document-structure", type(appel(HttpMethod.POST, "/api/v1/envelopes/" + id + "/documents/" + tronque + "/confirmation", null, null)));
        assertEquals("depot-non-recu", type(appel(HttpMethod.POST, "/api/v1/envelopes/" + id + "/documents/" + UUID.randomUUID() + "/confirmation", null, null)));
        assertEquals("depot-invalide", type(mvc.perform(MockMvcRequestBuilders.put("/api/v1/depots/inconnu").contentType("application/pdf")
                .content(Pdfs.pages(1))).andReturn().getResponse()));
        assertEquals(200, appel(HttpMethod.PUT, "/api/v1/envelopes/" + id + "/signers", signataires("Alice", 1), null).getStatus());
        assertEquals("document-requis", type(appel(HttpMethod.POST, "/api/v1/envelopes/" + id + "/send", null, null)),
                "aucun document retenu : l'envoi est refusé");
        String mode = creer(empreinte);
        assertEquals("mode-sans-document", type(appel(HttpMethod.POST, "/api/v1/envelopes/" + mode + "/documents", null, null)));
        String vide = URI.create(corps(appel(HttpMethod.POST, "/api/v1/envelopes/" + creer(document) + "/documents", null, null)).get("url").asText()).getPath();
        assertEquals("document-taille", type(mvc.perform(MockMvcRequestBuilders.put(vide).contentType("application/pdf").content(new byte[0]))
                .andReturn().getResponse()), "document vide");
        // Fichier d'essai antivirus normalisé (EICAR), assemblé à l'exécution pour que ce source ne soit pas lui-même signalé.
        String essai = "X5O!P%@AP[4\\PZX54(P^)7CC)7}$" + "EICAR-STANDARD-ANTIVIRUS-" + "TEST-FILE!$H+H*";
        String eicar = deposer(id, essai.getBytes(java.nio.charset.StandardCharsets.US_ASCII));
        assertEquals("document-infecte", type(appel(HttpMethod.POST, "/api/v1/envelopes/" + id + "/documents/" + eicar + "/confirmation", null, null)));
    }

    @Test
    void les_signataires_respectent_leurs_types_et_referentiels() throws Exception {
        String id = creer(empreinte);
        ArrayNode sansSujet = json.createArrayNode();
        sansSujet.addObject().put("nom", "Dr Roy").put("langue", langue()).put("type", typeParExigence.get("sujet")).put("ordre", 1);
        assertEquals("sujet-externe-requis", type(appel(HttpMethod.PUT, "/api/v1/envelopes/" + id + "/signers", sansSujet, null)));
        ((ObjectNode) sansSujet.get(0)).put("sujetExterne", "utilisateur-42");
        assertEquals(200, appel(HttpMethod.PUT, "/api/v1/envelopes/" + id + "/signers", sansSujet, null).getStatus());
        ArrayNode sansCourriel = json.createArrayNode();
        sansCourriel.addObject().put("nom", "Invité").put("langue", langue()).put("type", typeParExigence.get("courriel")).put("ordre", 1);
        assertEquals("courriel-requis", type(appel(HttpMethod.PUT, "/api/v1/envelopes/" + id + "/signers", sansCourriel, null)));
        ArrayNode langueInconnue = signataires("Alice", 1);
        ((ObjectNode) langueInconnue.get(0)).put("langue", "xx");
        assertEquals("valeur-referentiel-inconnue", type(appel(HttpMethod.PUT, "/api/v1/envelopes/" + id + "/signers", langueInconnue, null)));
        assertEquals(400, appel(HttpMethod.PUT, "/api/v1/envelopes/" + id + "/signers", json.createArrayNode(), null).getStatus());
    }

    @Test
    void annulation_invalide_les_depots_et_publie_l_evenement() throws Exception {
        String id = creer(document);
        MockHttpServletResponse demande = appel(HttpMethod.POST, "/api/v1/envelopes/" + id + "/documents", null, null);
        String url = URI.create(corps(demande).get("url").asText()).getPath();
        assertEquals("annulee", corps(appel(HttpMethod.POST, "/api/v1/envelopes/" + id + "/cancel", null, null)).get("statut").asText());
        assertEquals("depot-invalide", type(mvc.perform(MockMvcRequestBuilders.put(url).contentType("application/pdf").content(Pdfs.pages(1)))
                .andReturn().getResponse()), "le lien de dépôt est invalidé");
        assertEquals("transition-enveloppe-interdite", type(appel(HttpMethod.POST, "/api/v1/envelopes/" + id + "/cancel", null, null)));
        assertEquals("transition-enveloppe-interdite", type(appel(HttpMethod.POST, "/api/v1/envelopes/" + id + "/documents", null, null)),
                "plus aucun dépôt une fois annulée");
        assertEquals(List.of("envelope.cancelled"), sortants(id));
    }

    @Test
    void une_enveloppe_echue_expire_une_seule_fois_et_publie_l_evenement() throws Exception {
        MockHttpServletResponse r = appel(HttpMethod.POST, "/api/v1/envelopes", nouvelle(empreinte, Instant.now().plusSeconds(2)), UUID.randomUUID().toString());
        String id = corps(r).get("id").asText();
        appel(HttpMethod.PUT, "/api/v1/envelopes/" + id + "/signers", signataires("Alice", 1), null);
        assertEquals(200, appel(HttpMethod.POST, "/api/v1/envelopes/" + id + "/send", null, null).getStatus());
        Thread.sleep(2100);
        assertTrue(expiration.executer() >= 1);
        expiration.executer();
        assertEquals("expiree", corps(appel(HttpMethod.GET, "/api/v1/envelopes/" + id, null, null)).get("statut").asText());
        assertEquals(List.of("envelope.sent", "envelope.expired"), sortants(id), "une seule expiration, même relancée");
    }

    @Test
    void une_application_ne_voit_ni_ne_pilote_l_enveloppe_d_une_autre() throws Exception {
        String id = creer(empreinte);
        Application voisine = applications.enregistrer(app.organisation(), PORTEES.toArray(String[]::new));
        assertEquals("enveloppe-introuvable", type(appel(voisine, HttpMethod.GET, "/api/v1/envelopes/" + id, null, null)));
        assertEquals("enveloppe-introuvable", type(appel(voisine, HttpMethod.POST, "/api/v1/envelopes/" + id + "/cancel", null, null)));
        Application ailleurs = applications.enregistrer(PORTEES.toArray(String[]::new));
        assertEquals(404, appel(ailleurs, HttpMethod.GET, "/api/v1/envelopes/" + id, null, null).getStatus(), "autre organisation : RLS");
        var utilisateur = jwt().jwt(j -> j.claim("org", app.organisation().toString())).authorities(new SimpleGrantedAuthority("SCOPE_enveloppes-lire"));
        assertEquals("application-requise", type(mvc.perform(MockMvcRequestBuilders.get("/api/v1/envelopes/" + id).with(utilisateur)).andReturn().getResponse()));
        var sansPortee = jwt().jwt(j -> j.claim("org", app.organisation().toString()).claim("client_id", app.clientId()));
        assertEquals(403, mvc.perform(MockMvcRequestBuilders.get("/api/v1/envelopes/" + id).with(sansPortee)).andReturn().getResponse().getStatus());
    }

    @Test
    void le_quota_mensuel_du_plan_est_applique() throws Exception {
        String plan = "plan-quota-" + UUID.randomUUID();
        referentiels.ajouterValeurOrganisation("plan", app.organisation(), new ValeurReferentiel(new CodeReferentiel(plan),
                Libelles.de(Map.of("fr", "Quota d'essai")), 9, Map.of("requetesParMinute", "1000", "enveloppesParMois", "1"), true));
        new JdbcTemplate(proprietaire).update("UPDATE organisation SET plan = ? WHERE id = ?", plan, app.organisation());
        creer(empreinte);
        MockHttpServletResponse refus = appel(HttpMethod.POST, "/api/v1/envelopes", nouvelle(empreinte, Instant.now().plusSeconds(60)), UUID.randomUUID().toString());
        assertEquals(403, refus.getStatus());
        assertEquals("quota-enveloppes-atteint", type(refus));
    }

    @Test
    void aucune_route_ne_pose_un_statut() throws Exception {
        JsonNode schemas = corps(mvc.perform(MockMvcRequestBuilders.get("/v3/api-docs")).andReturn().getResponse()).path("components").path("schemas");
        for (JsonNode s : (Iterable<JsonNode>) schemas::elements) {
            assertFalse(s.path("properties").has("statut") && s.path("properties").size() < 3, "corps de requête qui poserait un statut : " + s);
        }
        JsonNode chemins = corps(mvc.perform(MockMvcRequestBuilders.get("/v3/api-docs")).andReturn().getResponse()).path("paths");
        chemins.fieldNames().forEachRemaining(chemin -> assertFalse(chemin.contains("status") || chemin.contains("statut"), chemin));
    }

    private String type(MockHttpServletResponse r) throws Exception {
        String t = corps(r).path("type").asText();
        return t.substring(t.lastIndexOf('/') + 1);
    }
}
