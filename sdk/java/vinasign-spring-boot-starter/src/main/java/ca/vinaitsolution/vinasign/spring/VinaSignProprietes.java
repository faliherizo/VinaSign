package ca.vinaitsolution.vinasign.spring;

import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Réglages {@code vinasign.*} de l'application cliente. La clé privée et le secret des webhooks viennent d'un coffre ou de
 * l'environnement, jamais du dépôt.
 *
 * @param url            adresse de l'API VinaSign
 * @param emetteur       émetteur des jetons (audience des assertions) ; l'adresse de l'API par défaut
 * @param clePrivee      clé privée PKCS#8 (PEM), RSA ou EC P-256
 * @param webhookSecret  secret remis à la création de la destination de webhooks
 */
@ConfigurationProperties("vinasign")
public record VinaSignProprietes(String url, String emetteur, String clientId, String clePrivee, String kid, List<String> portees,
                                 String webhookSecret, Duration webhookTolerance) {

    public VinaSignProprietes {
        portees = portees == null ? List.of() : List.copyOf(portees);
        webhookTolerance = webhookTolerance == null ? Duration.ofMinutes(5) : webhookTolerance;
    }

    public String emetteurEffectif() {
        return emetteur == null || emetteur.isBlank() ? url : emetteur;
    }
}
