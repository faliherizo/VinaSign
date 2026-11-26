package ca.vinaitsolution.vinasign.notification.infrastructure.jdbc;

import static org.junit.jupiter.api.Assertions.*;

import ca.vinaitsolution.vinasign.commun.application.port.ExecutionOrganisation;
import ca.vinaitsolution.vinasign.commun.domain.IdentifiantOrganisation;
import ca.vinaitsolution.vinasign.notification.application.port.BoiteEnvoi;
import ca.vinaitsolution.vinasign.notification.application.port.BoiteSms;
import ca.vinaitsolution.vinasign.notification.domain.CourrielSortant;
import ca.vinaitsolution.vinasign.support.ApplicationsDeTest;
import ca.vinaitsolution.vinasign.support.PostgresDeTest;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.liquibase.LiquibaseDataSource;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

/** Boîtes d'envoi (VS-080, VS-081) sur la vraie base : un échec reporte la tentative ; l'abandon efface le contenu. */
@SpringBootTest
@Import({PostgresDeTest.class, ApplicationsDeTest.class})
class BoitesEnvoiJdbcTest {

    private static final String SYSTEME = "plateforme:essai";

    @Autowired BoiteEnvoi courriels;
    @Autowired BoiteSms sms;
    @Autowired ExecutionOrganisation execution;
    @Autowired ApplicationsDeTest applications;
    @Autowired @LiquibaseDataSource DataSource proprietaire;

    @Test
    void un_courriel_en_echec_est_reporte_puis_abandonne_sans_garder_son_corps() {
        UUID org = applications.enregistrer().organisation();
        UUID id = UUID.randomUUID();
        Instant t = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        execution.pour(new IdentifiantOrganisation(org), SYSTEME, () -> {
            courriels.deposer(new CourrielSortant(id, org, "echec-" + id + "@exemple.ca", "Sujet", "Corps avec lien", 0, t, null));
            return null;
        });
        JdbcTemplate base = new JdbcTemplate(proprietaire);
        courriels.echec(id, 1, t.plusSeconds(60), false);
        Map<String, Object> reporte = base.queryForMap("SELECT tentatives, prochaine_tentative, abandonne_le, corps FROM courriel_sortant WHERE id = ?", id);
        assertEquals(1, reporte.get("tentatives"));
        assertEquals(t.plusSeconds(60), ((java.sql.Timestamp) reporte.get("prochaine_tentative")).toInstant());
        assertNull(reporte.get("abandonne_le"));
        assertNotNull(reporte.get("corps"), "gardé pour la prochaine tentative");
        courriels.echec(id, 8, t.plusSeconds(120), true);
        Map<String, Object> abandonne = base.queryForMap("SELECT abandonne_le, corps FROM courriel_sortant WHERE id = ?", id);
        assertNotNull(abandonne.get("abandonne_le"));
        assertNull(abandonne.get("corps"), "abandonné : le lien qu'il portait est effacé");
    }

    @Test
    void un_sms_en_echec_est_reporte_puis_abandonne_sans_garder_son_texte() {
        UUID org = applications.enregistrer().organisation();
        UUID id = UUID.randomUUID();
        Instant t = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        execution.pour(new IdentifiantOrganisation(org), SYSTEME, () -> {
            sms.deposer(id, org, "+15145550100", "Votre code : 123456", t);
            return null;
        });
        JdbcTemplate base = new JdbcTemplate(proprietaire);
        sms.echec(id, 1, t.plusSeconds(60), false);
        assertEquals(1, base.queryForObject("SELECT tentatives FROM sms_sortant WHERE id = ?", Integer.class, id));
        sms.echec(id, 8, t.plusSeconds(120), true);
        Map<String, Object> abandonne = base.queryForMap("SELECT abandonne_le, texte FROM sms_sortant WHERE id = ?", id);
        assertNotNull(abandonne.get("abandonne_le"));
        assertNull(abandonne.get("texte"));
    }
}
