package ca.vinaitsolution.vinasign.enveloppe.infrastructure.preuve;

import static org.junit.jupiter.api.Assertions.*;

import ca.vinaitsolution.vinasign.enveloppe.application.dto.Certificat;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class GabaritCertificatHtmlTest {

    private static final Instant T = Instant.parse("2026-11-22T15:04:05Z");

    private static String rendre(Certificat.Integrite integrite, List<Certificat.Signataire> signataires) {
        Certificat c = new Certificat("fr", Map.of("titre", "Certificat", "altere", "ALTÉRÉ", "aucun", "—"), "id-1", "<script>alert(1)</script>", "Terminée", T,
                List.of(new Certificat.Document("Final", "ab".repeat(32))), signataires,
                List.of(new Certificat.Evenement(T, "Créée", "application:\"x\" & 'y'")), integrite, T);
        return new String(new GabaritCertificatHtml().html(c), StandardCharsets.UTF_8);
    }

    @Test
    void toute_valeur_saisie_est_echappee_et_aucune_ressource_externe_n_est_appelee() {
        String html = rendre(new Certificat.Integrite(true, 3, "cd".repeat(32), T), List.of());
        assertFalse(html.contains("<script>"));
        assertTrue(html.contains("&lt;script&gt;alert(1)&lt;/script&gt;"));
        assertTrue(html.contains("application:&quot;x&quot; &amp; &#39;y&#39;"));
        assertFalse(html.matches("(?s).*(src=|href=|url\\().*"), "autonome : la conversion n'a aucun accès réseau");
        assertTrue(html.contains("2026-11-22 15:04:05 UTC"));
        assertTrue(html.contains("<th scope=\"col\">"), "tableaux à en-têtes");
        assertTrue(html.contains("non-ancre") == false && html.contains("cd".repeat(32)));
    }

    @Test
    void une_alteration_un_journal_non_ancre_et_les_signataires_sans_preuve_sont_rendus() {
        String html = rendre(new Certificat.Integrite(false, 3, null, null), List.of(
                new Certificat.Signataire(1, "Alice", "Signé", T, "v1 · ab", T, "Renforcé", List.of("pwd", "otp")),
                new Certificat.Signataire(2, "Bruno", "En attente", null, null, null, null, List.of()),
                new Certificat.Signataire(3, "Chloé", "Signé", T, "v1 · ab", T, "Simple", List.of())));
        assertTrue(html.contains("class=\"alerte\">ALTÉRÉ"));
        assertTrue(html.contains("non-ancre"), "un texte absent du référentiel s'affiche par son code");
        assertTrue(html.contains("Renforcé (amr : pwd, otp)"));
        assertTrue(html.contains("<td>Simple</td>"));
        assertTrue(html.contains("<td>Bruno</td><td>En attente</td><td>—</td><td>—</td><td>—</td>"));
        assertEquals("", GabaritCertificatHtml.e(null));
    }
}
