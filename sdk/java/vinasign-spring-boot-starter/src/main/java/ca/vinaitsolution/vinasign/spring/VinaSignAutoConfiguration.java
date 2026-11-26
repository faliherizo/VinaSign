package ca.vinaitsolution.vinasign.spring;

import ca.vinaitsolution.vinasign.client.Attestations;
import ca.vinaitsolution.vinasign.client.JetonsVinaSign;
import ca.vinaitsolution.vinasign.client.VinaSign;
import ca.vinaitsolution.vinasign.client.VinaSignWebhooks;
import java.net.URI;
import java.time.Clock;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * Autoconfiguration du client VinaSign (VS-092) : client et attestations dès que {@code vinasign.url} et
 * {@code vinasign.client-id} sont fournis ; vérification des webhooks dès que {@code vinasign.webhook-secret} l'est.
 */
@AutoConfiguration
@EnableConfigurationProperties(VinaSignProprietes.class)
public class VinaSignAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    Clock vinaSignHorloge() {
        return Clock.systemUTC();
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "vinasign", name = {"url", "client-id"})
    VinaSign vinaSign(VinaSignProprietes p, Clock horloge) {
        return new VinaSign(new JetonsVinaSign.Identite(URI.create(p.url()), p.emetteurEffectif(), p.clientId(), ClePem.lire(p.clePrivee()), p.kid(),
                p.portees()), horloge);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "vinasign", name = {"url", "client-id"})
    Attestations vinaSignAttestations(VinaSignProprietes p, Clock horloge) {
        return new Attestations(p.emetteurEffectif(), p.clientId(), ClePem.lire(p.clePrivee()), horloge);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "vinasign", name = "webhook-secret")
    VinaSignWebhooks vinaSignWebhooks(VinaSignProprietes p, Clock horloge) {
        return new VinaSignWebhooks(p.webhookSecret(), horloge, p.webhookTolerance());
    }
}
