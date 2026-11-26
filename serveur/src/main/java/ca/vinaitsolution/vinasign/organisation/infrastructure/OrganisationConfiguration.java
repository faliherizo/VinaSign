package ca.vinaitsolution.vinasign.organisation.infrastructure;

import ca.vinaitsolution.vinasign.commun.application.port.ActeurCourant;
import ca.vinaitsolution.vinasign.commun.application.port.GenerateurIdentifiant;
import ca.vinaitsolution.vinasign.commun.application.port.JournalAudit;
import ca.vinaitsolution.vinasign.commun.application.port.OrganisationCourante;
import ca.vinaitsolution.vinasign.commun.application.port.ExecutionOrganisation;
import ca.vinaitsolution.vinasign.commun.application.port.PublicationEvenements;
import ca.vinaitsolution.vinasign.organisation.application.AdministrerOrganisations;
import ca.vinaitsolution.vinasign.organisation.application.InscrireOrganisation;
import ca.vinaitsolution.vinasign.organisation.application.port.Inscriptions;
import ca.vinaitsolution.vinasign.organisation.domain.Inscription;
import ca.vinaitsolution.vinasign.organisation.infrastructure.dns.ResolutionDnsJndi;
import org.springframework.beans.factory.annotation.Value;
import ca.vinaitsolution.vinasign.organisation.application.GererMarque;
import ca.vinaitsolution.vinasign.organisation.application.port.Marques;
import ca.vinaitsolution.vinasign.organisation.application.port.Organisations;
import ca.vinaitsolution.vinasign.referentiel.application.port.Referentiels;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OrganisationConfiguration {

    @Bean
    AdministrerOrganisations administrerOrganisations(Organisations organisations, Referentiels referentiels, JournalAudit audit, ActeurCourant acteur,
                                                      GenerateurIdentifiant identifiants, Clock horloge) {
        return new AdministrerOrganisations(organisations, referentiels, audit, acteur, identifiants, horloge);
    }

    @Bean
    GererMarque gererMarque(Marques marques, OrganisationCourante organisation, Clock horloge) {
        return new GererMarque(marques, organisation, horloge);
    }

    /**
     * Inscription en libre-service (VS-122) : « fermee » (défaut), « bac-a-sable » (courriel confirmé suffit) ou « production »
     * (domaine prouvé et validation par l'administrateur de la plateforme).
     */
    @Bean
    InscrireOrganisation inscrireOrganisation(Inscriptions inscriptions, ExecutionOrganisation execution, PublicationEvenements publication,
                                              GenerateurIdentifiant identifiants, java.security.SecureRandom alea, Clock horloge,
                                              AdministrerOrganisations organisations,
                                              @Value("${vinasign.inscription.mode:fermee}") String mode,
                                              @Value("${vinasign.inscription.dns:}") String dns,
                                              @Value("${vinasign.inscription.duree-lien:PT24H}") java.time.Duration duree,
                                              @Value("${vinasign.inscription.url-confirmation:http://localhost:4300/inscription}") String url) {
        Inscription.Politique politique = switch (mode) {
            case "bac-a-sable" -> new Inscription.Politique(false, false);
            case "production" -> new Inscription.Politique(true, true);
            default -> null;
        };
        return new InscrireOrganisation(new InscrireOrganisation.Ports(inscriptions, new ResolutionDnsJndi(dns), execution, publication),
                new InscrireOrganisation.Outils(identifiants, alea, horloge), organisations, new InscrireOrganisation.Reglages(politique, duree, url));
    }
}
