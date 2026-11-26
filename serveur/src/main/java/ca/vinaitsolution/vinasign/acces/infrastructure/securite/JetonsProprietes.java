package ca.vinaitsolution.vinasign.acces.infrastructure.securite;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Réglages des jetons (VS-012), tous tirés de l'environnement.
 *
 * @param emetteur              URL publique du serveur d'autorisation ({@code iss}), ex. https://api.vinasign.ca
 * @param audience              audience des jetons d'accès ({@code aud}), exigée par l'API
 * @param duree                 durée de vie d'un jeton d'accès (5 min par défaut)
 * @param dureeMaxAssertion     durée de vie maximale acceptée pour une assertion client
 * @param clePriveePem          clé RSA de signature des jetons (PKCS#8, PEM), depuis le coffre de secrets
 * @param cleEphemereAutorisee  vrai seulement sur un poste ou en test : une clé générée au démarrage (jetons perdus au redémarrage)
 */
@Validated
@ConfigurationProperties("vinasign.jetons")
public record JetonsProprietes(@NotBlank String emetteur, @NotBlank String audience, @NotNull Duration duree,
                               @NotNull Duration dureeMaxAssertion, String clePriveePem, boolean cleEphemereAutorisee) {

    @Override
    public String toString() {
        return "JetonsProprietes[emetteur=" + emetteur + ", audience=" + audience + ", duree=" + duree + "]";
    }
}
