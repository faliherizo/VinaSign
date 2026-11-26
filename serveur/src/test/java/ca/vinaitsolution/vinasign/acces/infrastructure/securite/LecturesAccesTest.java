package ca.vinaitsolution.vinasign.acces.infrastructure.securite;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import ca.vinaitsolution.vinasign.acces.application.dto.VueJeton;
import ca.vinaitsolution.vinasign.acces.infrastructure.web.JetonController;
import ca.vinaitsolution.vinasign.commun.infrastructure.VinaSignProprietes;
import ca.vinaitsolution.vinasign.referentiel.application.ConsulterReferentiel;
import ca.vinaitsolution.vinasign.referentiel.application.ListerReferentiels;
import ca.vinaitsolution.vinasign.referentiel.infrastructure.web.ReferentielController;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

class LecturesAccesTest {

    @Test
    void un_jeton_sans_portee_rend_une_liste_vide() {
        Jwt jeton = Jwt.withTokenValue("t").header("alg", "RS256").subject("essai-12345678").claim("org", "o").build();
        VueJeton vue = new JetonController().jeton(jeton);
        assertEquals(List.of(), vue.portees());
        assertEquals("o", vue.organisation());
    }

    @Test
    void une_langue_de_navigateur_vide_retombe_sur_la_langue_principale() {
        ListerReferentiels lister = mock(ListerReferentiels.class);
        new ReferentielController(lister, mock(ConsulterReferentiel.class), new VinaSignProprietes("fr", List.of())).lister(null, "");
        verify(lister).executer(new ca.vinaitsolution.vinasign.commun.domain.CodeLangue("fr"));
    }
}
