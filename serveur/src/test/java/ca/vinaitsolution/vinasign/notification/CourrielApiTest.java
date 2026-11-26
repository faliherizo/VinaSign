package ca.vinaitsolution.vinasign.notification;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

import ca.vinaitsolution.vinasign.notification.application.ExpedierCourriels;
import ca.vinaitsolution.vinasign.support.ApplicationsDeTest;
import ca.vinaitsolution.vinasign.support.ApplicationsDeTest.Application;
import ca.vinaitsolution.vinasign.support.CourrielDeTest;
import ca.vinaitsolution.vinasign.support.InvitationsCapturees;
import ca.vinaitsolution.vinasign.support.PostgresDeTest;
import ca.vinaitsolution.vinasign.support.ReferentielsSql;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.liquibase.LiquibaseDataSource;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/** VS-080 — invitation par courriel, de l'envoi de l'enveloppe à la boîte du destinataire (Mailpit réel). */
@SpringBootTest
@AutoConfigureMockMvc
@Import({PostgresDeTest.class, ApplicationsDeTest.class, ReferentielsSql.class, CourrielDeTest.class, InvitationsCapturees.class})
@ExtendWith(OutputCaptureExtension.class)
class CourrielApiTest {

    private static final List<String> PORTEES = List.of("enveloppes-creer", "enveloppes-envoyer", "signataires-gerer");

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired ApplicationsDeTest applications;
    @Autowired ExpedierCourriels expedition;
    @Autowired InvitationsCapturees invitations;
    @Autowired @LiquibaseDataSource DataSource proprietaire;

    private String code(String referentiel, String code) throws Exception {
        for (JsonNode v : json.readTree(mvc.perform(MockMvcRequestBuilders.get("/api/v1/referentiels/" + referentiel)).andReturn().getResponse()
                .getContentAsString()).get("valeurs")) {
            if (v.get("code").asText().equals(code)) return code;
        }
        throw new IllegalStateException(referentiel + " sans " + code);
    }

    @Test
    void chaque_signataire_invite_recoit_dans_sa_langue_son_lien_personnel_sans_piece_jointe(CapturedOutput sortie) throws Exception {
        Application app = applications.enregistrer(PORTEES.toArray(String[]::new));
        var jeton = jwt().jwt(j -> j.subject(app.clientId()).claim("org", app.organisation().toString()).claim("client_id", app.clientId()))
                .authorities(PORTEES.stream().<GrantedAuthority>map(p -> new SimpleGrantedAuthority("SCOPE_" + p)).toList());
        String reference = "REF-" + UUID.randomUUID();
        String id = json.readTree(mvc.perform(MockMvcRequestBuilders.post("/api/v1/envelopes").with(jeton).header("Idempotency-Key", reference)
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("externalReference", reference,
                        "mode", code("mode-enveloppe", "empreinte"), "expiresAt", Instant.now().plusSeconds(86400).toString()))))
                .andReturn().getResponse().getContentAsString()).get("id").asText();
        String fr = "fr-" + UUID.randomUUID() + "@exemple.ca";
        String en = "en-" + UUID.randomUUID() + "@exemple.ca";
        mvc.perform(MockMvcRequestBuilders.put("/api/v1/envelopes/" + id + "/signers").with(jeton).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(List.of(
                        Map.of("nom", "Alice", "courriel", fr, "langue", code("langue", "fr"), "type", code("type-signataire", "invite"), "ordre", 1),
                        Map.of("nom", "Ann", "courriel", en, "langue", code("langue", "en"), "type", code("type-signataire", "invite"), "ordre", 1)))));
        mvc.perform(MockMvcRequestBuilders.post("/api/v1/envelopes/" + id + "/send").with(jeton));

        assertTrue(expedition.executer() >= 2);
        JsonNode recuFr = CourrielDeTest.recus(fr).get(0);
        assertEquals("Document à signer : " + reference, recuFr.get("Subject").asText());
        JsonNode complet = CourrielDeTest.message(recuFr.get("ID").asText());
        String jetonFr = invitations.pour(id).stream().filter(a -> !a.get("jeton").isBlank()).map(a -> a.get("jeton")).filter(t -> complet.get("Text").asText().contains(t))
                .findFirst().orElseThrow(() -> new AssertionError("le lien personnel figure dans le courriel"));
        assertTrue(complet.get("Text").asText().contains("/s/" + jetonFr));
        assertEquals(0, complet.get("Attachments").size(), "aucun document joint");
        assertEquals("Document to sign: " + reference, CourrielDeTest.recus(en).get(0).get("Subject").asText());

        assertEquals(0, new JdbcTemplate(proprietaire).queryForObject(
                "SELECT count(*) FROM courriel_sortant WHERE destinataire IN (?, ?) AND (corps IS NOT NULL OR envoye_le IS NULL)", Integer.class, fr, en),
                "envoyés, et le corps qui portait le lien est effacé");
        assertFalse(sortie.getOut().contains(fr) || sortie.getOut().contains(en), "le destinataire n'est jamais journalisé en clair");
        assertEquals(0, expedition.executer() - expedition.executer(), "rien n'est renvoyé");
    }

    @Test
    void aucune_invitation_ne_part_apres_une_annulation_meme_deja_deposee() throws Exception {
        Application app = applications.enregistrer(PORTEES.toArray(String[]::new));
        var jeton = jwt().jwt(j -> j.subject(app.clientId()).claim("org", app.organisation().toString()).claim("client_id", app.clientId()))
                .authorities(PORTEES.stream().<GrantedAuthority>map(p -> new SimpleGrantedAuthority("SCOPE_" + p)).toList());
        String reference = "ANN-" + UUID.randomUUID();
        String id = json.readTree(mvc.perform(MockMvcRequestBuilders.post("/api/v1/envelopes").with(jeton).header("Idempotency-Key", reference)
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("externalReference", reference,
                        "mode", code("mode-enveloppe", "empreinte"), "expiresAt", Instant.now().plusSeconds(86400).toString()))))
                .andReturn().getResponse().getContentAsString()).get("id").asText();
        String destinataire = "annule-" + UUID.randomUUID() + "@exemple.ca";
        mvc.perform(MockMvcRequestBuilders.put("/api/v1/envelopes/" + id + "/signers").with(jeton).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(List.of(Map.of("nom", "Dana", "courriel", destinataire, "langue", code("langue", "fr"),
                        "type", code("type-signataire", "invite"), "ordre", 1)))));
        mvc.perform(MockMvcRequestBuilders.post("/api/v1/envelopes/" + id + "/send").with(jeton));
        var annulation = jwt().jwt(j -> j.subject(app.clientId()).claim("org", app.organisation().toString()).claim("client_id", app.clientId()))
                .authorities(new SimpleGrantedAuthority("SCOPE_enveloppes-envoyer"));
        mvc.perform(MockMvcRequestBuilders.post("/api/v1/envelopes/" + id + "/cancel").with(annulation));
        expedition.executer();
        assertEquals(0, CourrielDeTest.recus(destinataire).size(), "rien n'est envoyé pour une enveloppe annulée");
        assertEquals(1, new JdbcTemplate(proprietaire).queryForObject(
                "SELECT count(*) FROM courriel_sortant WHERE destinataire = ? AND abandonne_le IS NOT NULL AND corps IS NULL", Integer.class, destinataire));
    }
}
