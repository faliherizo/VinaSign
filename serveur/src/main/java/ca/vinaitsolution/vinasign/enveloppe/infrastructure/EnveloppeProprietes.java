package ca.vinaitsolution.vinasign.enveloppe.infrastructure;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Réglages des enveloppes et documents, tirés de l'environnement.
 *
 * @param urlPublique adresse publique de l'API, préfixe des URL de dépôt
 */
@Validated
@ConfigurationProperties("vinasign.enveloppes")
public record EnveloppeProprietes(@NotBlank String urlPublique, @NotNull Duration validiteDepot, @Min(1) long tailleMax,
                                  @Valid Stockage stockage, @Valid Antivirus antivirus, @Valid Expiration expiration,
                                  @Valid Conversion conversion) {

    /** Service de conversion bureautique → PDF/A (Gotenberg), isolé du réseau. */
    public record Conversion(@NotBlank String url, @NotNull Duration delai) {
    }

    /**
     * Connexion (poste, test : chaîne de connexion Azurite) ou point de terminaison (exploitation : identité gérée).
     * {@code creerConteneur} : poste et test seulement ; en exploitation, Terraform crée le conteneur.
     */
    public record Stockage(String connexion, String pointDeTerminaison, @NotBlank String conteneur, boolean creerConteneur) {
    }

    public record Antivirus(@NotBlank String hote, @Min(1) int port, @NotNull Duration delai) {
    }

    public record Expiration(@Min(1) int lot) {
    }
}
