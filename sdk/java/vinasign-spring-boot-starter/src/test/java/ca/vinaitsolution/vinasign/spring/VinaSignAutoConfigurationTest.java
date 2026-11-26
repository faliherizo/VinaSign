package ca.vinaitsolution.vinasign.spring;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

import ca.vinaitsolution.vinasign.client.Attestations;
import ca.vinaitsolution.vinasign.client.VinaSign;
import ca.vinaitsolution.vinasign.client.VinaSignWebhooks;
import java.security.KeyPairGenerator;
import java.security.spec.ECGenParameterSpec;
import java.time.Duration;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class VinaSignAutoConfigurationTest {

    private final ApplicationContextRunner contexte = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(VinaSignAutoConfiguration.class));

    private static String pem(String algorithme) throws Exception {
        KeyPairGenerator g = KeyPairGenerator.getInstance(algorithme);
        if ("EC".equals(algorithme)) {
            g.initialize(new ECGenParameterSpec("secp256r1"));
        } else {
            g.initialize(2048);
        }
        return "-----BEGIN PRIVATE KEY-----\n" + Base64.getMimeEncoder().encodeToString(g.generateKeyPair().getPrivate().getEncoded())
                + "\n-----END PRIVATE KEY-----\n";
    }

    @Test
    void sans_reglages_rien_n_est_cree() {
        contexte.run(c -> {
            assertThat(c).doesNotHaveBean(VinaSign.class);
            assertThat(c).doesNotHaveBean(VinaSignWebhooks.class);
        });
    }

    @Test
    void le_client_les_attestations_et_les_webhooks_sont_configures() throws Exception {
        String cle = pem("EC");
        contexte.withPropertyValues("vinasign.url=https://api.vinasign.test", "vinasign.client-id=app-1", "vinasign.cle-privee=" + cle,
                "vinasign.portees=enveloppes-lire,enveloppes-creer", "vinasign.webhook-secret=s3cret", "vinasign.webhook-tolerance=PT2M").run(c -> {
            assertThat(c).hasSingleBean(VinaSign.class).hasSingleBean(Attestations.class).hasSingleBean(VinaSignWebhooks.class);
            VinaSignProprietes p = c.getBean(VinaSignProprietes.class);
            assertEquals("https://api.vinasign.test", p.emetteurEffectif(), "émetteur : l'adresse de l'API par défaut");
            assertEquals(Duration.ofMinutes(2), p.webhookTolerance());
            assertEquals(2, p.portees().size());
        });
        contexte.withPropertyValues("vinasign.url=https://api.vinasign.test", "vinasign.emetteur=https://auth.vinasign.test", "vinasign.client-id=app-1",
                "vinasign.cle-privee=" + pem("RSA")).run(c -> {
            assertEquals("https://auth.vinasign.test", c.getBean(VinaSignProprietes.class).emetteurEffectif());
            assertEquals(Duration.ofMinutes(5), c.getBean(VinaSignProprietes.class).webhookTolerance());
            assertThat(c).doesNotHaveBean(VinaSignWebhooks.class);
        });
    }

    @Test
    void une_cle_absente_ou_illisible_est_refusee_au_demarrage() throws Exception {
        assertThrows(IllegalArgumentException.class, () -> ClePem.lire(null));
        assertThrows(IllegalArgumentException.class, () -> ClePem.lire("-----BEGIN RSA PRIVATE KEY-----"));
        assertThrows(IllegalArgumentException.class, () -> ClePem.lire("-----BEGIN PRIVATE KEY-----\nAAAA\n-----END PRIVATE KEY-----"));
        contexte.withPropertyValues("vinasign.url=https://api.vinasign.test", "vinasign.client-id=app-1", "vinasign.emetteur= ").run(c ->
                assertThat(c).hasFailed());
    }
}
