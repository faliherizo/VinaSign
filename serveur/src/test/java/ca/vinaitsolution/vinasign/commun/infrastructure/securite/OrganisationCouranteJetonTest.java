package ca.vinaitsolution.vinasign.commun.infrastructure.securite;

import static org.junit.jupiter.api.Assertions.*;

import ca.vinaitsolution.vinasign.commun.domain.IdentifiantOrganisation;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

class OrganisationCouranteJetonTest {

    private final OrganisationCouranteJeton adaptateur = new OrganisationCouranteJeton();

    @AfterEach
    void vider() {
        SecurityContextHolder.clearContext();
    }

    private static Jwt jeton(String org) {
        Jwt.Builder b = Jwt.withTokenValue("x").header("alg", "RS256").subject("client").issuedAt(Instant.now());
        if (org != null) b.claim(OrganisationCouranteJeton.REVENDICATION, org);
        return b.build();
    }

    @Test
    void l_organisation_vient_de_la_revendication_du_jeton() {
        UUID org = UUID.randomUUID();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jeton(org.toString())));
        assertEquals(new IdentifiantOrganisation(org), adaptateur.courante().orElseThrow());
    }

    @Test
    void sans_jeton_sans_revendication_ou_avec_une_autre_authentification_aucune_organisation() {
        assertTrue(adaptateur.courante().isEmpty());
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jeton(null)));
        assertTrue(adaptateur.courante().isEmpty());
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken("x", "y"));
        assertTrue(adaptateur.courante().isEmpty());
    }

    @Test
    void une_revendication_mal_formee_est_refusee() {
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jeton("pas-un-uuid")));
        assertThrows(IllegalArgumentException.class, adaptateur::courante);
    }
}
