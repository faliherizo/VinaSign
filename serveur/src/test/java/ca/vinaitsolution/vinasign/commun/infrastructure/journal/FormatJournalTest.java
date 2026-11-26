package ca.vinaitsolution.vinasign.commun.infrastructure.journal;

import static org.junit.jupiter.api.Assertions.*;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.LoggingEvent;
import ch.qos.logback.classic.util.LogbackMDCAdapter;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.junit.jupiter.api.Test;

class FormatJournalTest {

    private final ObjectMapper json = new ObjectMapper();
    private final Logger journal = contexte().getLogger("vinasign.essai");

    private static LoggerContext contexte() {
        LoggerContext contexte = new LoggerContext();
        contexte.setMDCAdapter(new LogbackMDCAdapter());
        return contexte;
    }

    private JsonNode formater(LoggingEvent e) throws Exception {
        String ligne = new FormatJournal().format(e);
        assertTrue(ligne.endsWith("\n"), "une ligne par événement");
        return json.readTree(ligne);
    }

    @Test
    void ecrit_un_evenement_json_masque_et_relie_a_sa_trace() throws Exception {
        LoggingEvent e = new LoggingEvent("x", journal, Level.WARN, "invitation pour {}", null, new Object[] {"a@b.ca"});
        e.setMDCPropertyMap(Map.of("traceId", "t-1", "spanId", "s-1"));
        e.setTimeStamp(0);

        JsonNode ligne = formater(e);

        assertEquals("invitation pour [courriel]", ligne.get("message").asText());
        assertEquals("WARN", ligne.get("log.level").asText());
        assertEquals("vinasign.essai", ligne.get("log.logger").asText());
        assertEquals("vinasign", ligne.get("service.name").asText());
        assertEquals("1970-01-01T00:00:00Z", ligne.get("@timestamp").asText());
        assertEquals("t-1", ligne.get("trace.id").asText());
        assertEquals("s-1", ligne.get("span.id").asText());
        assertFalse(ligne.has("error.type"));
    }

    @Test
    void une_erreur_ne_livre_que_son_type_et_son_message_masque() throws Exception {
        LoggingEvent e = new LoggingEvent("x", journal, Level.ERROR, "échec", new IllegalStateException("Bearer abc"), null);

        JsonNode ligne = formater(e);

        assertEquals(IllegalStateException.class.getName(), ligne.get("error.type").asText());
        assertEquals("Bearer [jeton]", ligne.get("error.message").asText());
        assertFalse(ligne.has("trace.id"), "sans trace active, aucun identifiant inventé");
        assertFalse(ligne.toString().contains("at ca.vinaitsolution"), "aucune pile d'appels");
    }

    @Test
    void sans_contexte_de_diagnostic_l_evenement_reste_ecrit() throws Exception {
        LoggingEvent e = new LoggingEvent("x", journal, Level.INFO, "prêt", null, null);
        e.setMDCPropertyMap(null);

        assertEquals("prêt", formater(e).get("message").asText());
    }
}
