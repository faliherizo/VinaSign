package ca.vinaitsolution.vinasign.organisation.infrastructure;

import static org.junit.jupiter.api.Assertions.*;

import ca.vinaitsolution.vinasign.organisation.infrastructure.dns.ResolutionDnsJndi;
import ca.vinaitsolution.vinasign.support.DnsDeTest;
import java.util.List;
import org.junit.jupiter.api.Test;

/** VS-122 — l'enregistrement TXT publié se lit ; un nom inexistant ou un serveur injoignable rend une liste vide. */
class ResolutionDnsJndiTest {

    @Test
    void les_textes_publies_se_lisent_et_un_nom_absent_rend_une_liste_vide() throws Exception {
        try (DnsDeTest dns = new DnsDeTest()) {
            dns.publier("_vinasign-verification.exemple.ca", "vinasign-abc");
            ResolutionDnsJndi resolution = new ResolutionDnsJndi(dns.adresse());
            assertEquals(List.of("vinasign-abc"), resolution.textes("_vinasign-verification.exemple.ca"));
            assertEquals(List.of(), resolution.textes("absent.exemple.ca"));
            dns.publier("sans-txt.exemple.ca", "");
            assertEquals(List.of(), resolution.textes("sans-txt.exemple.ca"), "nom existant, aucun TXT");
        }
    }

    @Test
    void un_serveur_injoignable_rend_une_liste_vide() throws Exception {
        String adresse;
        try (DnsDeTest ferme = new DnsDeTest()) {
            adresse = ferme.adresse();
        }
        assertEquals(List.of(), new ResolutionDnsJndi(adresse).textes("exemple.ca"));
        assertNotNull(new ResolutionDnsJndi(null));
    }
}
