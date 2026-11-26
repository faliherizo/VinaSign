package ca.vinaitsolution.vinasign.commun.infrastructure.securite;

import static org.junit.jupiter.api.Assertions.*;

import ca.vinaitsolution.vinasign.commun.domain.Acteur;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

class ActeurCouranteJetonTest {

    @AfterEach
    void vider() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void l_acteur_est_le_nom_de_l_authentification() {
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken("client-1", null, List.of()));
        assertEquals(new Acteur("client-1"), new ActeurCouranteJeton().courant());
    }

    @Test
    void sans_authentification_ou_non_authentifie_aucune_action_n_est_attribuable() {
        assertThrows(AuthenticationCredentialsNotFoundException.class, () -> new ActeurCouranteJeton().courant());
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("x", null));
        assertThrows(AuthenticationCredentialsNotFoundException.class, () -> new ActeurCouranteJeton().courant());
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(null, null, List.of()));
        assertThrows(AuthenticationCredentialsNotFoundException.class, () -> new ActeurCouranteJeton().courant());
    }
}
