package ca.vinaitsolution.vinasign.commun.infrastructure.securite;

import static org.junit.jupiter.api.Assertions.*;

import ca.vinaitsolution.vinasign.commun.domain.IdentifiantOrganisation;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

/** Les authentifications de la plateforme ne portent aucun secret ; seule une session de signataire désigne un signataire courant. */
class AuthentificationsTest {

    private static final IdentifiantOrganisation ORG = new IdentifiantOrganisation(UUID.randomUUID());

    @AfterEach
    void vider() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void aucun_secret_n_est_conserve_dans_le_contexte_de_securite() {
        assertNull(new AuthentificationSignataire(ORG, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID()).getCredentials());
        assertNull(new AuthentificationTache(ORG, "plateforme:essai").getCredentials());
    }

    @Test
    void seul_un_signataire_en_session_est_le_signataire_courant() {
        SignataireCourantJeton courant = new SignataireCourantJeton();
        assertTrue(courant.courant().isEmpty(), "personne");
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken("application", null));
        assertTrue(courant.courant().isEmpty(), "une application n'est pas un signataire");
        SecurityContextHolder.getContext().setAuthentication(new AuthentificationSignataire(ORG, UUID.randomUUID(), UUID.randomUUID(), null));
        assertTrue(courant.courant().isEmpty(), "lien ouvert, session pas encore créée");
        UUID session = UUID.randomUUID();
        SecurityContextHolder.getContext().setAuthentication(new AuthentificationSignataire(ORG, UUID.randomUUID(), UUID.randomUUID(), session));
        assertEquals(session, courant.courant().orElseThrow().session());
    }
}
