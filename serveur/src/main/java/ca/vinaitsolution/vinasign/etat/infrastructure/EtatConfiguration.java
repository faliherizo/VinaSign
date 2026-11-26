package ca.vinaitsolution.vinasign.etat.infrastructure;

import ca.vinaitsolution.vinasign.commun.application.port.GenerateurIdentifiant;
import ca.vinaitsolution.vinasign.etat.application.ConsulterEtat;
import ca.vinaitsolution.vinasign.etat.application.GererIncidents;
import ca.vinaitsolution.vinasign.etat.application.port.Incidents;
import ca.vinaitsolution.vinasign.referentiel.application.ExigerValeurReferentiel;
import ca.vinaitsolution.vinasign.referentiel.application.port.Referentiels;
import java.time.Clock;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class EtatConfiguration {

    @Bean
    GererIncidents gererIncidents(Incidents incidents, ExigerValeurReferentiel referentiel, GenerateurIdentifiant identifiants, Clock horloge) {
        return new GererIncidents(incidents, referentiel, identifiants, horloge);
    }

    @Bean
    ConsulterEtat consulterEtat(Incidents incidents, Referentiels referentiels, Clock horloge,
                                @Value("${vinasign.etat.historique:P90D}") Duration historique) {
        return new ConsulterEtat(incidents, referentiels, horloge, historique);
    }
}
