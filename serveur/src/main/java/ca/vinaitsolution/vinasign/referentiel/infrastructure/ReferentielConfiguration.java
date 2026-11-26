package ca.vinaitsolution.vinasign.referentiel.infrastructure;

import ca.vinaitsolution.vinasign.commun.domain.CodeLangue;
import ca.vinaitsolution.vinasign.commun.infrastructure.VinaSignProprietes;
import ca.vinaitsolution.vinasign.referentiel.application.ConsulterReferentiel;
import ca.vinaitsolution.vinasign.referentiel.application.ExigerValeurReferentiel;
import ca.vinaitsolution.vinasign.referentiel.application.ListerReferentiels;
import ca.vinaitsolution.vinasign.referentiel.application.port.Referentiels;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Assemblage des cas d'utilisation : la couche application reste libre de tout framework. */
@Configuration
public class ReferentielConfiguration {

    @Bean
    ListerReferentiels listerReferentiels(Referentiels referentiels, VinaSignProprietes proprietes) {
        return new ListerReferentiels(referentiels, new CodeLangue(proprietes.languePrincipale()));
    }

    @Bean
    ConsulterReferentiel consulterReferentiel(Referentiels referentiels, VinaSignProprietes proprietes) {
        return new ConsulterReferentiel(referentiels, new CodeLangue(proprietes.languePrincipale()));
    }

    @Bean
    ExigerValeurReferentiel exigerValeurReferentiel(Referentiels referentiels) {
        return new ExigerValeurReferentiel(referentiels);
    }
}
