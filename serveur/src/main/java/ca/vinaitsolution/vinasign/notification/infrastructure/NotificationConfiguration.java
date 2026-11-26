package ca.vinaitsolution.vinasign.notification.infrastructure;

import ca.vinaitsolution.vinasign.commun.application.port.GenerateurIdentifiant;
import ca.vinaitsolution.vinasign.commun.application.port.LectureEnveloppes;
import ca.vinaitsolution.vinasign.commun.domain.CodeLangue;
import ca.vinaitsolution.vinasign.commun.infrastructure.VinaSignProprietes;
import ca.vinaitsolution.vinasign.notification.application.EnvoyerCode;
import ca.vinaitsolution.vinasign.notification.application.ExpedierCourriels;
import ca.vinaitsolution.vinasign.notification.application.InviterParCourriel;
import ca.vinaitsolution.vinasign.notification.application.ModelesCourriel;
import ca.vinaitsolution.vinasign.notification.application.ExpedierSms;
import ca.vinaitsolution.vinasign.notification.application.port.BoiteEnvoi;
import ca.vinaitsolution.vinasign.notification.application.port.BoiteSms;
import ca.vinaitsolution.vinasign.notification.application.port.Sms;
import ca.vinaitsolution.vinasign.notification.infrastructure.sms.SmsSimulateur;
import ca.vinaitsolution.vinasign.notification.application.port.Messagerie;
import ca.vinaitsolution.vinasign.notification.infrastructure.smtp.MessagerieSmtp;
import ca.vinaitsolution.vinasign.referentiel.application.port.Referentiels;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import java.time.Clock;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.validation.annotation.Validated;

@Configuration
@EnableConfigurationProperties(NotificationConfiguration.NotificationProprietes.class)
public class NotificationConfiguration {

    /**
     * @param urlSignataire adresse publique de l'interface du signataire (liens d'invitation)
     * @param expediteur    adresse d'expédition
     */
    @Validated
    @ConfigurationProperties("vinasign.notification")
    public record NotificationProprietes(@NotBlank String urlSignataire, @NotBlank String expediteur, @Min(1) int lot, @Min(1) int tentativesMax,
                                         String fournisseurSms, String environnement) {
    }

    @Bean
    ModelesCourriel modelesCourriel(Referentiels referentiels, VinaSignProprietes vinasign) {
        return new ModelesCourriel(referentiels, new CodeLangue(vinasign.languePrincipale()));
    }

    @Bean
    ca.vinaitsolution.vinasign.notification.application.ConfirmerParCourriel confirmerParCourriel(BoiteEnvoi boite, LectureEnveloppes enveloppes,
            ModelesCourriel modeles, GenerateurIdentifiant identifiants, Clock horloge) {
        return new ca.vinaitsolution.vinasign.notification.application.ConfirmerParCourriel(boite, enveloppes, modeles, identifiants, horloge);
    }

    @Bean
    InviterParCourriel inviterParCourriel(BoiteEnvoi boite, LectureEnveloppes enveloppes, ModelesCourriel modeles, GenerateurIdentifiant identifiants,
                                          Clock horloge, NotificationProprietes p) {
        return new InviterParCourriel(boite, enveloppes, modeles, identifiants, horloge, p.urlSignataire());
    }

    @Bean
    EnvoyerCode envoyerCode(BoiteEnvoi boite, BoiteSms boiteSms, LectureEnveloppes enveloppes, ModelesCourriel modeles,
                            GenerateurIdentifiant identifiants, Clock horloge) {
        return new EnvoyerCode(boite, boiteSms, enveloppes, modeles, identifiants, horloge);
    }

    @Bean
    ca.vinaitsolution.vinasign.notification.application.ConfirmerInscription confirmerInscription(BoiteEnvoi boite, ModelesCourriel modeles,
                                                                                                  GenerateurIdentifiant identifiants, Clock horloge) {
        return new ca.vinaitsolution.vinasign.notification.application.ConfirmerInscription(boite, modeles, identifiants, horloge);
    }

    /** Fournisseur de SMS : le simulateur sur un poste et en bac à sable seulement ; le fournisseur réel, choisi en phase 0. */
    @Bean
    Sms sms(NotificationProprietes p, org.springframework.jdbc.core.JdbcTemplate jdbc) {
        if (!"simulateur".equals(p.fournisseurSms())) {
            return (numero, texte) -> {
                throw new IllegalStateException("Aucun fournisseur de SMS configuré (vinasign.notification.fournisseur-sms).");
            };
        }
        if ("production".equals(p.environnement())) {
            throw new IllegalStateException("Simulateur de SMS refusé en production.");
        }
        return new SmsSimulateur(jdbc);
    }

    @Bean
    ExpedierSms expedierSms(BoiteSms boite, Sms sms, Clock horloge, NotificationProprietes p) {
        return new ExpedierSms(boite, sms, horloge, p.lot(), p.tentativesMax());
    }

    @Bean
    Messagerie messagerie(JavaMailSender relais, NotificationProprietes p) {
        return new MessagerieSmtp(relais, p.expediteur());
    }

    @Bean
    ExpedierCourriels expedierCourriels(BoiteEnvoi boite, Messagerie messagerie, Clock horloge, NotificationProprietes p) {
        return new ExpedierCourriels(boite, messagerie, horloge, p.lot(), p.tentativesMax());
    }

    @Bean
    ExpeditionSmsPlanifiee expeditionSmsPlanifiee(ExpedierSms expedition) {
        return new ExpeditionSmsPlanifiee(expedition);
    }

    @Bean
    ExpeditionPlanifiee expeditionPlanifiee(ExpedierCourriels expedition) {
        return new ExpeditionPlanifiee(expedition);
    }

    /** Expédition régulière de la boîte d'envoi. */
    public static class ExpeditionPlanifiee {

        private final ExpedierCourriels expedition;

        ExpeditionPlanifiee(ExpedierCourriels expedition) {
            this.expedition = expedition;
        }

        @Scheduled(fixedDelayString = "${vinasign.notification.intervalle:PT30S}", initialDelayString = "${vinasign.notification.intervalle:PT30S}")
        public void expedier() {
            expedition.executer();
        }
    }

    /** Expédition régulière des SMS. */
    public static class ExpeditionSmsPlanifiee {

        private final ExpedierSms expedition;

        ExpeditionSmsPlanifiee(ExpedierSms expedition) {
            this.expedition = expedition;
        }

        @Scheduled(fixedDelayString = "${vinasign.notification.intervalle:PT30S}", initialDelayString = "${vinasign.notification.intervalle:PT30S}")
        public void expedier() {
            expedition.executer();
        }
    }
}
