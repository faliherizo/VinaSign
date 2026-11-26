package ca.vinaitsolution.vinasign.commun.infrastructure.journal;

import static org.junit.jupiter.api.Assertions.*;

import ca.vinaitsolution.vinasign.support.PostgresDeTest;
import ca.vinaitsolution.vinasign.support.ReferentielsSql;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

/** Sur l'application démarrée : la configuration réelle branche le journal structuré et masqué (VS-004). */
@SpringBootTest
@AutoConfigureMockMvc
@Import({PostgresDeTest.class, ReferentielsSql.class})
@TestPropertySource(properties = "vinasign.origines-autorisees=https://portail.exemple")
@ExtendWith(OutputCaptureExtension.class)
class JournalisationTest {

    @Test
    void la_sortie_de_l_application_est_du_json_sans_renseignement_personnel(CapturedOutput sortie) {
        LoggerFactory.getLogger("vinasign.essai").warn("invitation pour {} au {}", "marie@exemple.ca", "418-555-0199");

        String ligne = sortie.getOut().lines().filter(l -> l.contains("vinasign.essai")).findFirst().orElseThrow();
        assertTrue(ligne.startsWith("{"), ligne);
        assertTrue(ligne.contains("invitation pour [courriel] au [telephone]"), ligne);
        assertFalse(ligne.contains("marie@exemple.ca"));
    }
}
