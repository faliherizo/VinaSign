package ca.vinaitsolution.vinasign.commun.infrastructure.web;

import static org.junit.jupiter.api.Assertions.*;

import ca.vinaitsolution.vinasign.commun.application.port.ControleAcces;
import ca.vinaitsolution.vinasign.commun.domain.ContexteAcces;
import ca.vinaitsolution.vinasign.commun.domain.IdentifiantOrganisation;
import ca.vinaitsolution.vinasign.commun.domain.RefusAcces;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class FiltreControlesAccesTest {

    private final ContexteAcces contexte = new ContexteAcces(Optional.of(new IdentifiantOrganisation(UUID.randomUUID())), Optional.of("vs-1"));

    private MockFilterChain filtrer(List<ControleAcces> controles, MockHttpServletResponse reponse) throws Exception {
        MockFilterChain suite = new MockFilterChain();
        new FiltreControlesAcces(() -> contexte, controles, new ObjectMapper()).doFilter(new MockHttpServletRequest(), reponse, suite);
        return suite;
    }

    @Test
    void sans_refus_la_requete_continue_et_chaque_controle_voit_le_contexte() throws Exception {
        List<ContexteAcces> vus = new ArrayList<>();
        MockFilterChain suite = filtrer(List.of(c -> { vus.add(c); return Optional.empty(); }), new MockHttpServletResponse());
        assertNotNull(suite.getRequest());
        assertEquals(List.of(contexte), vus);
    }

    @Test
    void le_premier_refus_arrete_la_requete_par_un_probleme_403_type() throws Exception {
        MockHttpServletResponse reponse = new MockHttpServletResponse();
        MockFilterChain suite = filtrer(List.of(c -> Optional.of(new RefusAcces("application-revoquee", "révoquée")),
                c -> fail("le contrôle suivant n'est pas consulté")), reponse);
        assertNull(suite.getRequest(), "la requête n'atteint pas l'API");
        assertEquals(403, reponse.getStatus());
        assertEquals("application/problem+json", reponse.getContentType());
        assertTrue(reponse.getContentAsString().contains("/problemes/application-revoquee"));
        assertTrue(reponse.getContentAsString().contains("révoquée"));
    }

    @Test
    void un_contexte_d_acces_et_un_refus_sont_bien_formes() {
        assertEquals(ContexteAcces.anonyme(), new ContexteAcces(null, null));
        assertThrows(IllegalArgumentException.class, () -> new RefusAcces(null, "m"));
        assertThrows(IllegalArgumentException.class, () -> new RefusAcces(" ", "m"));
        assertThrows(IllegalArgumentException.class, () -> new RefusAcces("t", null));
        assertThrows(IllegalArgumentException.class, () -> new RefusAcces("t", " "));
    }
}
