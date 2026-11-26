package ca.vinaitsolution.vinasign.commun.infrastructure;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;
import jakarta.validation.constraints.NotBlank;

/**
 * Réglages de la plateforme, tous externes (variables d'environnement) : aucun hôte, origine ni langue en dur.
 *
 * @param languePrincipale langue de repli des libellés (code ISO 639-1, doit exister dans le référentiel {@code langue})
 * @param originesAutorisees origines Web autorisées par CORS (portail, signataire)
 */
@Validated
@ConfigurationProperties("vinasign")
public record VinaSignProprietes(@NotBlank String languePrincipale, List<String> originesAutorisees) {

    public VinaSignProprietes {
        originesAutorisees = originesAutorisees == null ? List.of() : List.copyOf(originesAutorisees);
    }
}
