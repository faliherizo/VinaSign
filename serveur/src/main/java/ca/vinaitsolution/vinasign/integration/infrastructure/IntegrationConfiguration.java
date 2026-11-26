package ca.vinaitsolution.vinasign.integration.infrastructure;

import ca.vinaitsolution.vinasign.commun.application.port.Chiffrement;
import ca.vinaitsolution.vinasign.commun.application.port.ContexteAccesCourant;
import ca.vinaitsolution.vinasign.commun.application.port.ExecutionOrganisation;
import ca.vinaitsolution.vinasign.commun.application.port.GenerateurIdentifiant;
import ca.vinaitsolution.vinasign.commun.domain.CodeLangue;
import ca.vinaitsolution.vinasign.integration.application.GererWebhooks;
import ca.vinaitsolution.vinasign.integration.application.LivrerWebhooks;
import ca.vinaitsolution.vinasign.integration.application.port.DestinationsWebhook;
import ca.vinaitsolution.vinasign.integration.application.port.EnvoiWebhook;
import ca.vinaitsolution.vinasign.integration.application.port.GardeDestinations;
import ca.vinaitsolution.vinasign.integration.application.port.GenerateurSecret;
import ca.vinaitsolution.vinasign.integration.application.port.Livraisons;
import ca.vinaitsolution.vinasign.integration.infrastructure.http.Sortant;
import ca.vinaitsolution.vinasign.referentiel.application.ConsulterReferentiel;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.validation.annotation.Validated;

/** Webhooks signés (VS-091) : destinations, garde anti-SSRF, livraison planifiée. */
@Configuration
@EnableConfigurationProperties(IntegrationConfiguration.WebhooksProprietes.class)
public class IntegrationConfiguration {

    /**
     * @param autorises hôte:port admis malgré la garde (bacs à sable, tests) ; vide en production
     * @param delai     délai de connexion et de réponse d'une destination
     */
    @Validated
    @ConfigurationProperties("vinasign.webhooks")
    public record WebhooksProprietes(Set<String> autorises, @NotNull Duration delai, @Min(1) int lot) {

        public WebhooksProprietes {
            autorises = autorises == null ? Set.of() : Set.copyOf(autorises);
        }
    }

    @Bean
    GardeDestinations gardeDestinations(WebhooksProprietes p) {
        return Sortant.garde(p.autorises());
    }

    @Bean
    EnvoiWebhook envoiWebhook(WebhooksProprietes p) {
        return Sortant.envoi(p.delai());
    }

    @Bean
    GenerateurSecret generateurSecret(SecureRandom alea) {
        return () -> {
            byte[] secret = new byte[32];
            alea.nextBytes(secret);
            return secret;
        };
    }

    @Bean
    GererWebhooks gererWebhooks(DestinationsWebhook destinations, Livraisons livraisons, ContexteAccesCourant acces, ConsulterReferentiel referentiels,
                                GardeDestinations garde, GenerateurSecret secrets, Chiffrement chiffrement, GenerateurIdentifiant identifiants,
                                Clock horloge, @Value("${vinasign.langue-principale:fr}") String langue) {
        return new GererWebhooks(new GererWebhooks.Registres(destinations, livraisons, referentiels, new CodeLangue(langue)), acces,
                new GererWebhooks.Securite(garde, secrets, chiffrement), new GererWebhooks.Horodatage(identifiants, horloge));
    }

    @Bean
    LivrerWebhooks livrerWebhooks(Livraisons livraisons, EnvoiWebhook envoi, GardeDestinations garde, Chiffrement chiffrement,
                                  ExecutionOrganisation execution, Clock horloge, WebhooksProprietes p) {
        return new LivrerWebhooks(livraisons, new LivrerWebhooks.Canal(envoi, garde, chiffrement), execution, new LivrerWebhooks.Cadence(horloge, p.lot()));
    }

    @Bean
    ca.vinaitsolution.vinasign.integration.infrastructure.mcp.ServeurMcp serveurMcp(ca.vinaitsolution.vinasign.commun.application.port.LectureEnveloppes enveloppes,
            ConsulterReferentiel referentiels, ca.vinaitsolution.vinasign.commun.application.port.JournalAudit audit,
            ca.vinaitsolution.vinasign.commun.application.port.ActeurCourant acteur, GenerateurIdentifiant identifiants, Clock horloge,
            com.fasterxml.jackson.databind.ObjectMapper json, @Value("${vinasign.langue-principale:fr}") String langue) {
        return new ca.vinaitsolution.vinasign.integration.infrastructure.mcp.ServeurMcp(java.util.List.of(
                ca.vinaitsolution.vinasign.integration.infrastructure.mcp.OutilsVinaSign.lireEnveloppe(enveloppes),
                ca.vinaitsolution.vinasign.integration.infrastructure.mcp.OutilsVinaSign.lireReferentiel(referentiels, new CodeLangue(langue))),
                new ca.vinaitsolution.vinasign.integration.infrastructure.mcp.ServeurMcp.Trace(audit, acteur, identifiants, horloge), json);
    }

    @Bean
    LivraisonPlanifiee livraisonPlanifiee(LivrerWebhooks livraison) {
        return new LivraisonPlanifiee(livraison);
    }

    /** Livraison régulière ; les échéances de reprise sont portées par chaque livraison. */
    public static class LivraisonPlanifiee {

        private final LivrerWebhooks livraison;

        LivraisonPlanifiee(LivrerWebhooks livraison) {
            this.livraison = livraison;
        }

        @Scheduled(fixedDelayString = "${vinasign.webhooks.intervalle:PT10S}", initialDelayString = "${vinasign.webhooks.intervalle:PT10S}")
        public void livrer() {
            livraison.executer();
        }
    }
}
