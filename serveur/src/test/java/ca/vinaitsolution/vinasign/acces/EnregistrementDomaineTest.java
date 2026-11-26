package ca.vinaitsolution.vinasign.acces;

import static org.junit.jupiter.api.Assertions.*;

import ca.vinaitsolution.vinasign.acces.domain.ApplicationCliente;
import ca.vinaitsolution.vinasign.acces.domain.CleDejaEnregistree;
import ca.vinaitsolution.vinasign.acces.domain.ClesPubliques;
import ca.vinaitsolution.vinasign.acces.domain.ClesPubliquesInvalides;
import ca.vinaitsolution.vinasign.acces.domain.IdentifiantClient;
import ca.vinaitsolution.vinasign.acces.domain.OrganisationRequise;
import ca.vinaitsolution.vinasign.acces.domain.OrigineIntegration;
import ca.vinaitsolution.vinasign.acces.domain.StatutApplication;
import ca.vinaitsolution.vinasign.commun.domain.ErreurMetier.Nature;
import ca.vinaitsolution.vinasign.commun.domain.IdentifiantOrganisation;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class EnregistrementDomaineTest {

    private static final ClesPubliques CLES = new ClesPubliques("{\"keys\":[]}", Set.of("e1"));

    @ParameterizedTest
    @CsvSource({
            "https://app.exemple.ca, https://app.exemple.ca",
            "HTTPS://App.Exemple.CA, https://app.exemple.ca",
            "https://app.exemple.ca:443, https://app.exemple.ca",
            "https://app.exemple.ca:8443, https://app.exemple.ca:8443",
            "'  https://app.exemple.ca  ', https://app.exemple.ca"})
    void une_origine_https_est_normalisee(String saisie, String attendue) {
        assertEquals(attendue, new OrigineIntegration(saisie).valeur());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"http://app.exemple.ca", "https://app.exemple.ca/", "https://app.exemple.ca/chemin",
            "https://app.exemple.ca?x=1", "https://app.exemple.ca#f", "https://moi@app.exemple.ca", "https://", "app.exemple.ca",
            "https://app exemple.ca", "*", "https:opaque", "https:///chemin"})
    void une_origine_approximative_est_refusee(String saisie) {
        assertThrows(IllegalArgumentException.class, () -> new OrigineIntegration(saisie));
    }

    @Test
    void une_application_enregistree_est_active_avec_ses_origines_et_un_nom_epure() {
        ApplicationCliente a = ApplicationCliente.enregistrer(UUID.randomUUID(), new IdentifiantOrganisation(UUID.randomUUID()),
                new IdentifiantClient("vs-12345678"), "  Système RH ", "production", CLES, Set.of("enveloppes-lire"),
                Set.of(new OrigineIntegration("https://rh.exemple.ca")));
        assertTrue(a.active());
        assertEquals("Système RH", a.nom());
        assertEquals(CLES.jwks(), a.clesPubliques());
        assertEquals(Set.of(new OrigineIntegration("https://rh.exemple.ca")), a.origines());
        assertEquals(a.origines(), a.revoquer(java.time.Instant.now()).origines(), "la révocation garde les origines");
    }

    @Test
    void un_environnement_absent_ou_un_nom_demesure_est_refuse() {
        UUID id = UUID.randomUUID();
        IdentifiantOrganisation o = new IdentifiantOrganisation(UUID.randomUUID());
        IdentifiantClient c = new IdentifiantClient("vs-12345678");
        assertThrows(IllegalArgumentException.class, () -> new ApplicationCliente(id, o, c, "n", null, "{}", null, null, StatutApplication.ACTIVE, null));
        assertThrows(IllegalArgumentException.class, () -> new ApplicationCliente(id, o, c, "n", " ", "{}", null, null, StatutApplication.ACTIVE, null));
        assertThrows(IllegalArgumentException.class, () -> new ApplicationCliente(id, o, c, "x".repeat(121), "e", "{}", null, null,
                StatutApplication.ACTIVE, null));
    }

    @Test
    void des_cles_vides_sont_refusees() {
        assertThrows(IllegalArgumentException.class, () -> new ClesPubliques(null, Set.of("e")));
        assertThrows(IllegalArgumentException.class, () -> new ClesPubliques(" ", Set.of("e")));
        assertThrows(IllegalArgumentException.class, () -> new ClesPubliques("{}", null));
        assertThrows(IllegalArgumentException.class, () -> new ClesPubliques("{}", Set.of()));
    }

    @Test
    void les_erreurs_d_enregistrement_ont_leur_nature_et_leur_type() {
        assertEquals(Nature.INVALIDE, new ClesPubliquesInvalides("x").nature());
        assertEquals("cles-publiques-invalides", new ClesPubliquesInvalides("x").type());
        assertEquals(Nature.CONFLIT, new CleDejaEnregistree().nature());
        assertEquals("cle-deja-enregistree", new CleDejaEnregistree().type());
        assertEquals(Nature.INTERDIT, new OrganisationRequise().nature());
        assertEquals("organisation-requise", new OrganisationRequise().type());
    }
}
