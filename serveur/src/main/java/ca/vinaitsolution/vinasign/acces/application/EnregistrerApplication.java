package ca.vinaitsolution.vinasign.acces.application;

import ca.vinaitsolution.vinasign.acces.application.dto.VueApplication;
import ca.vinaitsolution.vinasign.acces.application.port.RegistreApplications;
import ca.vinaitsolution.vinasign.acces.application.port.GenerateurIdentifiantClient;
import ca.vinaitsolution.vinasign.acces.application.port.VerificateurClesPubliques;
import ca.vinaitsolution.vinasign.acces.domain.ApplicationCliente;
import ca.vinaitsolution.vinasign.acces.domain.ClesPubliques;
import ca.vinaitsolution.vinasign.acces.domain.OrganisationRequise;
import ca.vinaitsolution.vinasign.acces.domain.OrigineIntegration;
import ca.vinaitsolution.vinasign.commun.application.port.ActeurCourant;
import ca.vinaitsolution.vinasign.commun.application.port.GenerateurIdentifiant;
import ca.vinaitsolution.vinasign.commun.application.port.JournalAudit;
import ca.vinaitsolution.vinasign.commun.application.port.OrganisationCourante;
import ca.vinaitsolution.vinasign.commun.domain.EvenementAudit;
import ca.vinaitsolution.vinasign.commun.domain.IdentifiantOrganisation;
import ca.vinaitsolution.vinasign.referentiel.application.ExigerValeurReferentiel;
import ca.vinaitsolution.vinasign.referentiel.domain.CodeReferentiel;
import java.time.Clock;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Enregistrement d'une application cliente par l'administrateur de son organisation (VS-011). L'organisation vient de
 * l'authentification ; l'environnement et les portées sont validés contre leurs référentiels ; les clés publiques
 * contre les règles de {@link VerificateurClesPubliques}.
 */
public class EnregistrerApplication {

    /** Demande d'enregistrement, telle que reçue. */
    public record Demande(String nom, String environnement, String jwks, Set<String> portees, Set<String> origines) {
    }

    static final CodeReferentiel ENVIRONNEMENTS = new CodeReferentiel("environnement-application");
    static final CodeReferentiel PORTEES = new CodeReferentiel("portee-api");
    static final String ACTION = "application-enregistree";

    private final RegistreApplications applications;
    private final VerificateurClesPubliques verificateur;
    private final ExigerValeurReferentiel referentiel;
    private final OrganisationCourante organisation;
    private final Collaborateurs collaborateurs;

    /** Services transverses : identifiants, acteur, audit, horloge. */
    public record Collaborateurs(GenerateurIdentifiantClient clients, GenerateurIdentifiant identifiants, ActeurCourant acteur,
                                 JournalAudit audit, Clock horloge) {
    }

    public EnregistrerApplication(RegistreApplications applications, VerificateurClesPubliques verificateur,
                                  ExigerValeurReferentiel referentiel, OrganisationCourante organisation, Collaborateurs collaborateurs) {
        this.applications = applications;
        this.verificateur = verificateur;
        this.referentiel = referentiel;
        this.organisation = organisation;
        this.collaborateurs = collaborateurs;
    }

    public VueApplication executer(Demande demande) {
        IdentifiantOrganisation org = organisation.courante().orElseThrow(OrganisationRequise::new);
        referentiel.executer(ENVIRONNEMENTS, new CodeReferentiel(demande.environnement()));
        Set<String> portees = demande.portees() == null ? Set.of() : demande.portees();
        portees.forEach(p -> referentiel.executer(PORTEES, new CodeReferentiel(p)));
        Set<OrigineIntegration> origines = demande.origines() == null ? Set.of()
                : demande.origines().stream().map(OrigineIntegration::new).collect(Collectors.toSet());
        ClesPubliques cles = verificateur.verifier(demande.jwks());
        ApplicationCliente application = ApplicationCliente.enregistrer(collaborateurs.identifiants().suivant(), org,
                collaborateurs.clients().suivant(), demande.nom(), demande.environnement(), cles, portees, origines);
        applications.ajouter(application, cles);
        collaborateurs.audit().consigner(new EvenementAudit(collaborateurs.identifiants().suivant(), org,
                collaborateurs.acteur().courant(), ACTION, application.client().valeur(), collaborateurs.horloge().instant()));
        return VueApplication.de(application);
    }

    public List<VueApplication> lister() {
        organisation.courante().orElseThrow(OrganisationRequise::new);
        return applications.deLOrganisationCourante().stream().map(VueApplication::de).toList();
    }
}
