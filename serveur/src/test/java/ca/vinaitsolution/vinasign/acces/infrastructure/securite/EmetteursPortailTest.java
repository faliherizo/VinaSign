package ca.vinaitsolution.vinasign.acces.infrastructure.securite;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

class EmetteursPortailTest {

    private final EmetteursConfiguration configuration = new EmetteursConfiguration();

    @Test
    void sans_emetteur_le_portail_est_ferme() {
        PortailProprietes ferme = new PortailProprietes(" ", null, null, null);
        assertFalse(ferme.estConfigure());
        assertNull(configuration.decodeurPortail(ferme).decodeur());
        assertEquals(List.of(), ferme.methodesMultifacteur());
    }

    @Test
    void un_portail_configure_a_moitie_empeche_le_demarrage() {
        assertThrows(IllegalStateException.class, () -> configuration.decodeurPortail(new PortailProprietes("https://idp", "https://idp/jwks", " ", null)));
        assertThrows(IllegalStateException.class, () -> configuration.decodeurPortail(new PortailProprietes("https://idp", null, "api", null)));
        assertThrows(IllegalStateException.class, () -> configuration.decodeurPortail(new PortailProprietes("https://idp", " ", "api", null)));
        assertNotNull(configuration.decodeurPortail(new PortailProprietes("https://idp", "https://idp/jwks", "api", List.of("mfa"))).decodeur());
    }

    @Test
    void un_courriel_atteste_mais_mal_forme_n_est_pas_retenu() {
        Jwt jeton = Jwt.withTokenValue("x").header("alg", "none").issuer("https://idp").subject("s").claim("email", "pas-un-courriel")
                .claim("email_verified", true).issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(60)).build();
        assertTrue(ConversionUtilisateurPortail.identite(jeton).courrielVerifie().isEmpty());
        assertTrue(ConversionUtilisateurPortail.identite(jeton).methodes().isEmpty(), "sans amr, aucune méthode");
    }
}
