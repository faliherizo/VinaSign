package ca.vinaitsolution.vinasign.acces.infrastructure;

import ca.vinaitsolution.vinasign.acces.application.EnregistrerApplication;
import ca.vinaitsolution.vinasign.acces.application.GererApplication;
import ca.vinaitsolution.vinasign.acces.application.GererUtilisateurs;
import ca.vinaitsolution.vinasign.acces.application.port.UtilisateursPortail;
import ca.vinaitsolution.vinasign.commun.application.port.StatutOrganisations;
import ca.vinaitsolution.vinasign.acces.application.port.RegistreApplications;
import ca.vinaitsolution.vinasign.acces.application.port.GenerateurIdentifiantClient;
import ca.vinaitsolution.vinasign.acces.application.port.VerificateurClesPubliques;
import ca.vinaitsolution.vinasign.commun.application.port.ActeurCourant;
import ca.vinaitsolution.vinasign.commun.application.port.GenerateurIdentifiant;
import ca.vinaitsolution.vinasign.commun.application.port.JournalAudit;
import ca.vinaitsolution.vinasign.commun.application.port.OrganisationCourante;
import ca.vinaitsolution.vinasign.referentiel.application.ExigerValeurReferentiel;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AccesConfiguration {

    /** Réinitialisation permise sur une plateforme déployée en bac à sable seulement (VINASIGN_ENVIRONNEMENT). */
    @Bean
    ca.vinaitsolution.vinasign.acces.application.ReinitialiserBacASable reinitialiserBacASable(
            ca.vinaitsolution.vinasign.acces.application.port.BacsASable bacs, ca.vinaitsolution.vinasign.commun.application.port.ContexteAccesCourant acces,
            GenerateurIdentifiant identifiants, Clock horloge,
            @org.springframework.beans.factory.annotation.Value("${vinasign.environnement:poste}") String environnement) {
        return new ca.vinaitsolution.vinasign.acces.application.ReinitialiserBacASable(bacs, acces, identifiants, horloge, "bac-a-sable".equals(environnement));
    }

    @Bean
    EnregistrerApplication.Collaborateurs collaborateursAcces(GenerateurIdentifiantClient clients, GenerateurIdentifiant identifiants,
                                                             ActeurCourant acteur, JournalAudit audit, Clock horloge) {
        return new EnregistrerApplication.Collaborateurs(clients, identifiants, acteur, audit, horloge);
    }

    @Bean
    EnregistrerApplication enregistrerApplication(RegistreApplications applications, VerificateurClesPubliques verificateur,
                                                  ExigerValeurReferentiel referentiel, OrganisationCourante organisation,
                                                  EnregistrerApplication.Collaborateurs collaborateurs) {
        return new EnregistrerApplication(applications, verificateur, referentiel, organisation, collaborateurs);
    }

    @Bean
    GererApplication gererApplication(RegistreApplications applications, VerificateurClesPubliques verificateur,
                                      OrganisationCourante organisation, EnregistrerApplication.Collaborateurs collaborateurs) {
        return new GererApplication(applications, verificateur, organisation, collaborateurs);
    }

    @Bean
    GererUtilisateurs gererUtilisateurs(UtilisateursPortail utilisateurs, ExigerValeurReferentiel referentiel,
                                        OrganisationCourante organisation, StatutOrganisations statuts,
                                        EnregistrerApplication.Collaborateurs collaborateurs) {
        return new GererUtilisateurs(utilisateurs, referentiel, organisation, statuts, collaborateurs);
    }

    @Bean
    ca.vinaitsolution.vinasign.acces.application.InviterAdministrateurInscrit inviterAdministrateurInscrit(GererUtilisateurs utilisateurs) {
        return new ca.vinaitsolution.vinasign.acces.application.InviterAdministrateurInscrit(utilisateurs);
    }
}
