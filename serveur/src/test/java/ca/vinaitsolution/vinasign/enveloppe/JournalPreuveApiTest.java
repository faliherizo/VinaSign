package ca.vinaitsolution.vinasign.enveloppe;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

import ca.vinaitsolution.vinasign.enveloppe.application.AncrerJournal;
import ca.vinaitsolution.vinasign.enveloppe.domain.Ancrage;
import ca.vinaitsolution.vinasign.support.ApplicationsDeTest;
import ca.vinaitsolution.vinasign.support.ApplicationsDeTest.Application;
import ca.vinaitsolution.vinasign.support.PostgresDeTest;
import ca.vinaitsolution.vinasign.support.ReferentielsSql;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.liquibase.LiquibaseDataSource;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/** VS-071 — journal de preuve chaîné par la base, en ajout seul, ancré et signé ; une altération est détectée. */
@SpringBootTest
@AutoConfigureMockMvc
@Import({PostgresDeTest.class, ApplicationsDeTest.class, ReferentielsSql.class})
@TestPropertySource(properties = "vinasign.preuve.ancrage.marge=PT0S")
class JournalPreuveApiTest {

    private static final List<String> PORTEES = List.of("enveloppes-creer", "enveloppes-lire", "signataires-gerer", "preuves-lire");

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired ApplicationsDeTest applications;
    @Autowired AncrerJournal ancrage;
    @Autowired @LiquibaseDataSource DataSource proprietaire;

    private Application app;

    /** Cachet d'essai (PKCS#12 généré) : il signe l'ancrage. */
    @org.springframework.test.context.DynamicPropertySource
    static void cachet(org.springframework.test.context.DynamicPropertyRegistry r) throws Exception {
        java.nio.file.Path dossier = java.nio.file.Files.createTempDirectory("cachet-ancrage");
        r.add("vinasign.cachet.fournisseur", () -> "pkcs12");
        r.add("vinasign.cachet.magasin", () -> ca.vinaitsolution.vinasign.signature.cle.Certificats.magasin(dossier, "RSA", "essai".toCharArray()).toString());
        r.add("vinasign.cachet.mot-de-passe", () -> "essai");
    }

    @BeforeEach
    void preparer() {
        app = applications.enregistrer(PORTEES.toArray(String[]::new));
    }

    private JwtRequestPostProcessor jeton() {
        return jwt().jwt(j -> j.subject(app.clientId()).claim("org", app.organisation().toString()).claim("client_id", app.clientId()))
                .authorities(PORTEES.stream().<GrantedAuthority>map(p -> new SimpleGrantedAuthority("SCOPE_" + p)).toList());
    }

    private JsonNode corps(MockHttpServletResponse r) throws Exception {
        return json.readTree(r.getContentAsString(StandardCharsets.UTF_8));
    }

    private String code(String referentiel, String code) throws Exception {
        for (JsonNode v : corps(mvc.perform(MockMvcRequestBuilders.get("/api/v1/referentiels/" + referentiel)).andReturn().getResponse()).get("valeurs")) {
            if (v.get("code").asText().equals(code)) return code;
        }
        throw new IllegalStateException(referentiel);
    }

    /** Enveloppe en brouillon, ses signataires définis deux fois : quatre événements. */
    private String enveloppe() throws Exception {
        String id = corps(mvc.perform(MockMvcRequestBuilders.post("/api/v1/envelopes").with(jeton()).header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("externalReference", "J-" + UUID.randomUUID(),
                        "mode", code("mode-enveloppe", "empreinte"), "expiresAt", Instant.now().plusSeconds(86400).toString()))))
                .andReturn().getResponse()).get("id").asText();
        for (String nom : List.of("Alice", "Alice | Tremblay")) {
            mvc.perform(MockMvcRequestBuilders.put("/api/v1/envelopes/" + id + "/signers").with(jeton()).contentType(MediaType.APPLICATION_JSON)
                    .content(json.writeValueAsString(List.of(Map.of("nom", nom, "courriel", "a@exemple.ca", "langue", code("langue", "fr"),
                            "type", code("type-signataire", "invite"), "ordre", 1)))));
        }
        return id;
    }

    private JsonNode integrite(String id) throws Exception {
        MockHttpServletResponse r = mvc.perform(MockMvcRequestBuilders.get("/api/v1/envelopes/" + id + "/preuve/integrite").with(jeton())).andReturn().getResponse();
        assertEquals(200, r.getStatus(), r.getContentAsString());
        return corps(r);
    }

    @Test
    void la_chaine_est_calculee_par_la_base_ancree_et_verifiee() throws Exception {
        String id = enveloppe();
        JsonNode avant = integrite(id);
        assertTrue(avant.get("integre").asBoolean());
        assertTrue(avant.get("evenements").asInt() >= 3);
        assertFalse(avant.hasNonNull("ancrage"), "pas encore ancré");
        Optional<Ancrage> a = ancrage.executer();
        assertTrue(a.isPresent() && a.get().nombre() >= 3);
        assertTrue(a.get().signature().length > 100, "signature CMS du cachet");
        JsonNode apres = integrite(id);
        assertTrue(apres.get("integre").asBoolean(), apres.toString());
        assertEquals(a.get().empreinteAgregee(), apres.get("ancrage").get("empreinteAgregee").asText());
        assertTrue(apres.get("ancrage").get("integre").asBoolean());
        assertTrue(ancrage.executer().isEmpty(), "rien de nouveau à ancrer");
    }

    @Test
    void le_journal_est_en_ajout_seul_meme_pour_le_proprietaire_et_une_alteration_est_detectee() throws Exception {
        String id = enveloppe();
        ancrage.executer();
        JdbcTemplate base = new JdbcTemplate(proprietaire);
        assertThrows(DataAccessException.class, () -> base.update("UPDATE evenement_enveloppe SET acteur = 'intrus' WHERE enveloppe_id = ?::uuid", id));
        assertThrows(DataAccessException.class, () -> base.update("DELETE FROM evenement_enveloppe WHERE enveloppe_id = ?::uuid", id));
        assertThrows(DataAccessException.class, () -> base.update("DELETE FROM ancrage_journal"));
        // Altération hors de l'application (accès direct à la base, déclencheur désactivé) : elle se voit à la vérification.
        base.execute("ALTER TABLE evenement_enveloppe DISABLE TRIGGER tg_journal_ajout_seul");
        try {
            base.update("UPDATE evenement_enveloppe SET acteur = 'intrus' WHERE enveloppe_id = ?::uuid AND sequence = 2", id);
        } finally {
            base.execute("ALTER TABLE evenement_enveloppe ENABLE TRIGGER tg_journal_ajout_seul");
        }
        JsonNode r = integrite(id);
        assertFalse(r.get("integre").asBoolean());
        assertEquals(2, r.get("rupture").asLong(), "falsification naïve : la chaîne se rompt au maillon modifié");

        // Falsification avertie : la chaîne est recalculée après la modification. Elle paraît intacte ; l'ancrage la trahit.
        base.execute("ALTER TABLE evenement_enveloppe DISABLE TRIGGER tg_journal_ajout_seul");
        try {
            base.execute(("DO $$ DECLARE r RECORD; prec CHAR(64); BEGIN "
                    + "SELECT empreinte INTO prec FROM evenement_enveloppe WHERE enveloppe_id = '%s' AND sequence = 1; "
                    + "FOR r IN SELECT * FROM evenement_enveloppe WHERE enveloppe_id = '%s' AND sequence >= 2 ORDER BY sequence LOOP "
                    + "UPDATE evenement_enveloppe SET empreinte_precedente = prec, "
                    + "empreinte = journal_empreinte(prec, r.enveloppe_id, r.sequence, r.type, r.acteur, r.survenu_le, r.donnees) WHERE id = r.id; "
                    + "SELECT empreinte INTO prec FROM evenement_enveloppe WHERE id = r.id; END LOOP; END $$").formatted(UUID.fromString(id), UUID.fromString(id)));
        } finally {
            base.execute("ALTER TABLE evenement_enveloppe ENABLE TRIGGER tg_journal_ajout_seul");
        }
        JsonNode avertie = integrite(id);
        assertFalse(avertie.hasNonNull("rupture"), "chaîne recalculée : cohérente en apparence");
        assertFalse(avertie.get("ancrage").get("integre").asBoolean(), "l'agrégat ancré et signé ne correspond plus");
        assertFalse(avertie.get("integre").asBoolean());
    }
}
