package ca.vinaitsolution.vinasign.acces.application;

import ca.vinaitsolution.vinasign.acces.application.dto.VueApplication;
import ca.vinaitsolution.vinasign.acces.application.port.RegistreApplications;
import ca.vinaitsolution.vinasign.acces.application.port.VerificateurClesPubliques;
import ca.vinaitsolution.vinasign.acces.domain.ApplicationCliente;
import ca.vinaitsolution.vinasign.acces.domain.ApplicationIntrouvable;
import ca.vinaitsolution.vinasign.acces.domain.ClesPubliques;
import ca.vinaitsolution.vinasign.acces.domain.IdentifiantClient;
import ca.vinaitsolution.vinasign.acces.domain.OrganisationRequise;
import ca.vinaitsolution.vinasign.commun.application.port.OrganisationCourante;
import ca.vinaitsolution.vinasign.commun.domain.EvenementAudit;
import ca.vinaitsolution.vinasign.commun.domain.IdentifiantOrganisation;

/**
 * Révocation et rotation des clés d'une application de l'organisation courante (VS-013), consignées au journal d'audit.
 * L'effet est immédiat : le point de jeton ne connaît plus une application révoquée, et le contrôle d'accès refuse ses
 * jetons déjà émis dès la requête suivante.
 */
public class GererApplication {

    static final String REVOQUEE = "application-revoquee";
    static final String CLES_RENOUVELEES = "cles-application-renouvelees";

    private final RegistreApplications registre;
    private final VerificateurClesPubliques verificateur;
    private final OrganisationCourante organisation;
    private final EnregistrerApplication.Collaborateurs collaborateurs;

    public GererApplication(RegistreApplications registre, VerificateurClesPubliques verificateur, OrganisationCourante organisation,
                            EnregistrerApplication.Collaborateurs collaborateurs) {
        this.registre = registre;
        this.verificateur = verificateur;
        this.organisation = organisation;
        this.collaborateurs = collaborateurs;
    }

    public VueApplication revoquer(IdentifiantClient client) {
        ApplicationCliente revoquee = trouver(client).revoquer(collaborateurs.horloge().instant());
        registre.remplacer(revoquee);
        consigner(revoquee, REVOQUEE);
        return VueApplication.de(revoquee);
    }

    public VueApplication renouvelerCles(IdentifiantClient client, String jwks) {
        ApplicationCliente actuelle = trouver(client);
        ClesPubliques cles = verificateur.verifier(jwks);
        ApplicationCliente suivante = actuelle.remplacerCles(cles);
        registre.remplacerCles(suivante, cles);
        consigner(suivante, CLES_RENOUVELEES);
        return VueApplication.de(suivante);
    }

    private ApplicationCliente trouver(IdentifiantClient client) {
        organisation.courante().orElseThrow(OrganisationRequise::new);
        return registre.deLOrganisation(client).orElseThrow(ApplicationIntrouvable::new);
    }

    private void consigner(ApplicationCliente a, String action) {
        IdentifiantOrganisation org = a.organisation();
        collaborateurs.audit().consigner(new EvenementAudit(collaborateurs.identifiants().suivant(), org, collaborateurs.acteur().courant(),
                action, a.client().valeur(), collaborateurs.horloge().instant()));
    }
}
