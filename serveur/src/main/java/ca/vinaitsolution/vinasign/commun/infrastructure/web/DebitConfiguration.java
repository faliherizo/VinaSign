package ca.vinaitsolution.vinasign.commun.infrastructure.web;

import ca.vinaitsolution.vinasign.commun.application.LimiterDebit;
import ca.vinaitsolution.vinasign.commun.application.port.ContexteAccesCourant;
import ca.vinaitsolution.vinasign.commun.application.port.PlanOrganisations;
import ca.vinaitsolution.vinasign.referentiel.application.port.Referentiels;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.validation.constraints.Min;
import java.time.Clock;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.validation.annotation.Validated;

/** Limitation du débit (VS-015) : filtre placé dans les chaînes de sécurité, seaux oubliés quand ils sont pleins. */
@Configuration
@EnableConfigurationProperties(DebitConfiguration.DebitProprietes.class)
public class DebitConfiguration {

    /** @param anonymeParMinute débit d'une adresse sans authentification (point de jeton, routes publiques) */
    @Validated
    @ConfigurationProperties("vinasign.debit")
    public record DebitProprietes(@Min(1) int anonymeParMinute) {
    }

    @Bean
    LimiterDebit limiterDebit(PlanOrganisations plans, Referentiels referentiels, DebitProprietes proprietes, Clock horloge) {
        return new LimiterDebit(plans, referentiels, proprietes.anonymeParMinute(), horloge);
    }

    @Bean
    FiltreLimiteDebit filtreLimiteDebit(LimiterDebit limiteur, ContexteAccesCourant contexte, MeterRegistry metriques, ObjectMapper json) {
        return new FiltreLimiteDebit(limiteur, contexte, metriques, json);
    }

    /** Le filtre est placé dans les chaînes de sécurité seulement, pas aussi dans la chaîne des servlets. */
    @Bean
    FilterRegistrationBean<FiltreLimiteDebit> debitHorsServlets(FiltreLimiteDebit filtre) {
        FilterRegistrationBean<FiltreLimiteDebit> inscription = new FilterRegistrationBean<>(filtre);
        inscription.setEnabled(false);
        return inscription;
    }

    /** Tâche d'entretien, non d'une configuration : elle borne la mémoire des seaux. */
    @Bean
    OubliSeaux oubliSeaux(LimiterDebit limiteur) {
        return new OubliSeaux(limiteur);
    }

    /** Oublie périodiquement les seaux pleins. */
    public static class OubliSeaux {

        private final LimiterDebit limiteur;

        OubliSeaux(LimiterDebit limiteur) {
            this.limiteur = limiteur;
        }

        @Scheduled(fixedDelayString = "${vinasign.debit.oubli:PT5M}")
        public void oublier() {
            limiteur.oublierInactifs();
        }
    }
}
