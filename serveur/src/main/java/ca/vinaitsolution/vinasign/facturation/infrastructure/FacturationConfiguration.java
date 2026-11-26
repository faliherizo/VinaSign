package ca.vinaitsolution.vinasign.facturation.infrastructure;

import ca.vinaitsolution.vinasign.commun.application.port.ExecutionOrganisation;
import ca.vinaitsolution.vinasign.commun.application.port.GenerateurIdentifiant;
import ca.vinaitsolution.vinasign.commun.application.port.OrganisationCourante;
import ca.vinaitsolution.vinasign.commun.application.port.SouscriptionPlans;
import ca.vinaitsolution.vinasign.facturation.application.GererAbonnement;
import ca.vinaitsolution.vinasign.facturation.application.port.Abonnements;
import ca.vinaitsolution.vinasign.facturation.application.port.EvenementsPaiement;
import ca.vinaitsolution.vinasign.facturation.application.port.Factures;
import ca.vinaitsolution.vinasign.facturation.application.port.PrestatairePaiement;
import ca.vinaitsolution.vinasign.facturation.infrastructure.stripe.PaiementAucun;
import ca.vinaitsolution.vinasign.facturation.infrastructure.stripe.StripePaiement;
import ca.vinaitsolution.vinasign.referentiel.application.ExigerValeurReferentiel;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(FacturationConfiguration.Proprietes.class)
public class FacturationConfiguration {

    /**
     * Prestataire de paiement (VS-121) : « aucun » (défaut) ou « stripe » ; clés par l'environnement ; prix Stripe de chaque
     * plan (code du référentiel plan → identifiant de prix), différents en mode test et en production.
     */
    @ConfigurationProperties("vinasign.paiement")
    public record Proprietes(String prestataire, String urlApi, String cleSecrete, String secretWebhook, Map<String, String> prix, String urlRetour) {
    }

    @Bean
    PrestatairePaiement prestatairePaiement(Proprietes p, Clock horloge, ObjectMapper json) {
        return "stripe".equals(p.prestataire())
                ? new StripePaiement(new StripePaiement.Acces(p.urlApi(), p.cleSecrete(), p.secretWebhook()),
                        new StripePaiement.Catalogue(p.prix() == null ? Map.of() : p.prix(), p.urlRetour()), horloge, json)
                : new PaiementAucun();
    }

    @Bean
    GererAbonnement gererAbonnement(PrestatairePaiement prestataire, Abonnements abonnements, Factures factures, EvenementsPaiement evenements,
                                    SouscriptionPlans plans, OrganisationCourante organisation, ExecutionOrganisation execution,
                                    ExigerValeurReferentiel referentiel, GenerateurIdentifiant identifiants, Clock horloge) {
        return new GererAbonnement(prestataire, new GererAbonnement.Depots(abonnements, factures, evenements, plans),
                new GererAbonnement.Contexte(organisation, execution, referentiel, identifiants, horloge));
    }
}
