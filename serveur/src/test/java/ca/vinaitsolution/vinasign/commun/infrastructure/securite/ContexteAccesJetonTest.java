package ca.vinaitsolution.vinasign.commun.infrastructure.securite;

import static org.junit.jupiter.api.Assertions.*;

import ca.vinaitsolution.vinasign.commun.domain.ContexteAcces;
import ca.vinaitsolution.vinasign.commun.domain.IdentifiantOrganisation;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

class ContexteAccesJetonTest {

    private final ContexteAccesJeton contexte = new ContexteAccesJeton(new OrganisationCouranteJeton());

    @AfterEach
    void vider() {
        SecurityContextHolder.clearContext();
    }

    private static void authentifier(Jwt.Builder jeton) {
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jeton.header("alg", "none")
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(60)).build()));
    }

    @Test
    void organisation_et_client_viennent_du_jeton() {
        UUID org = UUID.randomUUID();
        authentifier(Jwt.withTokenValue("x").subject("s").claim("org", org.toString()).claim("client_id", "vs-abc"));
        assertEquals(new ContexteAcces(Optional.of(new IdentifiantOrganisation(org)), Optional.of("vs-abc")), contexte.courant());
    }

    @Test
    void un_utilisateur_n_a_pas_de_client_et_une_requete_anonyme_rien() {
        authentifier(Jwt.withTokenValue("x").subject("utilisateur"));
        assertEquals(ContexteAcces.anonyme(), contexte.courant());
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken("autre", null, List.of()));
        assertEquals(ContexteAcces.anonyme(), contexte.courant());
        SecurityContextHolder.clearContext();
        assertEquals(ContexteAcces.anonyme(), contexte.courant());
    }

    @Test
    void preparateur_et_auditeur_du_portail_agissent_dans_l_espace_portail_les_autres_roles_non() {
        UUID org = UUID.randomUUID();
        for (String role : List.of("preparateur", "auditeur")) {
            SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(Jwt.withTokenValue("x").header("alg", "none").subject("u")
                    .claim("org", org.toString()).issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(60)).build(),
                    List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_" + role))));
            assertEquals(Optional.of(ContexteAcces.CLIENT_PORTAIL), contexte.courant().client(), role);
        }
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(Jwt.withTokenValue("x").header("alg", "none").subject("u")
                .claim("org", org.toString()).issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(60)).build(),
                List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_administrateur-organisation"))));
        assertEquals(Optional.empty(), contexte.courant().client());
    }
}
