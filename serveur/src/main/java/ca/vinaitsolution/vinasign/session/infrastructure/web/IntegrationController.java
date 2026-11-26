package ca.vinaitsolution.vinasign.session.infrastructure.web;

import ca.vinaitsolution.vinasign.session.application.AncetresIntegration;
import java.util.Set;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/**
 * Pages autorisées à intégrer l'interface du signataire (VS-055), interrogé par le frontal avant de servir
 * {@code /integre/{jeton}} : la politique {@code frame-ancestors} de la page en est tirée. Le jeton arrive dans un en-tête,
 * jamais dans l'URL ; la réponse ne dit pas s'il existe ({@code 'none'} pour tout lien sans intégration).
 */
@RestController
public class IntegrationController {

    static final String EN_TETE_JETON = "X-Jeton-Integration";
    static final String EN_TETE_ANCETRES = "X-Ancetres";
    private static final String AUCUN = "'none'";

    private final AncetresIntegration ancetres;

    public IntegrationController(AncetresIntegration ancetres) {
        this.ancetres = ancetres;
    }

    /** Sans transaction ici : la lecture se fait dans celle de l'organisation de l'enveloppe, ouverte par le cas d'usage. */
    @GetMapping("/api/v1/signataire/integration/ancetres")
    public ResponseEntity<Void> ancetres(@RequestHeader(name = EN_TETE_JETON, required = false) String jeton) {
        Set<String> origines = ancetres.pour(jeton);
        return ResponseEntity.noContent().header(EN_TETE_ANCETRES, origines.isEmpty() ? AUCUN : String.join(" ", origines))
                .header("Cache-Control", "no-store").build();
    }
}
