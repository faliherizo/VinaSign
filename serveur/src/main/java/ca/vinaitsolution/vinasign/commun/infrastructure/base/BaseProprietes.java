package ca.vinaitsolution.vinasign.commun.infrastructure.base;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Réglages de la base, tirés de l'environnement.
 *
 * @param roleApplicatif           compte de connexion de l'application : non propriétaire, soumis à la RLS, sans aucun
 *                                 autre rôle endossable. Provisionné hors migrations (Terraform, initialisation de la base).
 * @param motDePasseApplicatif     son mot de passe (coffre de secrets ou .env), jamais dans le dépôt
 */
@Validated
@ConfigurationProperties("vinasign.base")
public record BaseProprietes(@NotBlank @Pattern(regexp = "[a-z_][a-z0-9_]{0,62}") String roleApplicatif,
                             @NotBlank String motDePasseApplicatif) {

    @Override
    public String toString() {
        return "BaseProprietes[roleApplicatif=" + roleApplicatif + ", motDePasseApplicatif=***]";
    }
}
