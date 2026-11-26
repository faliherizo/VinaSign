package ca.vinaitsolution.vinasign.acces.application;

import ca.vinaitsolution.vinasign.acces.application.dto.VueUtilisateur;
import ca.vinaitsolution.vinasign.acces.application.port.UtilisateursPortail;
import ca.vinaitsolution.vinasign.acces.domain.Courriel;
import ca.vinaitsolution.vinasign.acces.domain.OrganisationInactive;
import ca.vinaitsolution.vinasign.acces.domain.OrganisationRequise;
import ca.vinaitsolution.vinasign.acces.domain.RoleNonAttribuable;
import ca.vinaitsolution.vinasign.acces.domain.UtilisateurIntrouvable;
import ca.vinaitsolution.vinasign.acces.domain.UtilisateurPortail;
import ca.vinaitsolution.vinasign.commun.application.port.OrganisationCourante;
import ca.vinaitsolution.vinasign.commun.application.port.StatutOrganisations;
import ca.vinaitsolution.vinasign.commun.domain.EvenementAudit;
import ca.vinaitsolution.vinasign.commun.domain.IdentifiantOrganisation;
import ca.vinaitsolution.vinasign.referentiel.application.ExigerValeurReferentiel;
import ca.vinaitsolution.vinasign.referentiel.domain.ValeurReferentiel;
import ca.vinaitsolution.vinasign.referentiel.domain.CodeReferentiel;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Utilisateurs du portail (VS-014) : l'administrateur d'une organisation invite, modifie et désactive les siens ;
 * l'administrateur de la plateforme invite le premier administrateur d'une organisation. Seuls les rôles du référentiel
 * {@code role-portail} de portée {@code organisation} s'attribuent ainsi.
 */
public class GererUtilisateurs {

    static final String ADMINISTRATEUR = "administrateur-organisation";
    static final String PORTEE = "portee";
    static final String PORTEE_ORGANISATION = "organisation";

    private final UtilisateursPortail utilisateurs;
    private final ExigerValeurReferentiel referentiel;
    private final OrganisationCourante organisation;
    private final StatutOrganisations statuts;
    private final EnregistrerApplication.Collaborateurs collaborateurs;

    public GererUtilisateurs(UtilisateursPortail utilisateurs, ExigerValeurReferentiel referentiel, OrganisationCourante organisation,
                             StatutOrganisations statuts, EnregistrerApplication.Collaborateurs collaborateurs) {
        this.utilisateurs = utilisateurs;
        this.referentiel = referentiel;
        this.organisation = organisation;
        this.statuts = statuts;
        this.collaborateurs = collaborateurs;
    }

    public VueUtilisateur inviter(String courriel, Set<String> roles) {
        IdentifiantOrganisation org = courante();
        UtilisateurPortail u = UtilisateurPortail.inviter(collaborateurs.identifiants().suivant(), org, new Courriel(courriel), attribuables(roles));
        utilisateurs.ajouter(u);
        consigner(org, u, "utilisateur-invite");
        return VueUtilisateur.de(u);
    }

    public VueUtilisateur inviterAdministrateur(IdentifiantOrganisation org, String courriel) {
        if (statuts.estBloquee(org)) {
            throw new OrganisationInactive();
        }
        UtilisateurPortail u = UtilisateurPortail.inviter(collaborateurs.identifiants().suivant(), org, new Courriel(courriel),
                attribuables(Set.of(ADMINISTRATEUR)));
        utilisateurs.ajouterPourLaPlateforme(u);
        consigner(org, u, "utilisateur-invite");
        return VueUtilisateur.de(u);
    }

    public List<VueUtilisateur> lister() {
        courante();
        return utilisateurs.deLOrganisationCourante().stream().map(VueUtilisateur::de).toList();
    }

    public VueUtilisateur modifierRoles(UUID id, Set<String> roles) {
        UtilisateurPortail u = trouver(id).avecRoles(attribuables(roles));
        utilisateurs.remplacer(u);
        consigner(u.organisation().orElseThrow(), u, "roles-utilisateur-modifies");
        return VueUtilisateur.de(u);
    }

    public VueUtilisateur desactiver(UUID id) {
        UtilisateurPortail u = trouver(id).desactiver();
        utilisateurs.remplacer(u);
        consigner(u.organisation().orElseThrow(), u, "utilisateur-desactive");
        return VueUtilisateur.de(u);
    }

    private Set<String> attribuables(Set<String> roles) {
        if (roles == null || roles.isEmpty()) {
            throw new IllegalArgumentException("Au moins un rôle est requis.");
        }
        for (String code : roles) {
            ValeurReferentiel role = referentiel.executer(AuthentifierUtilisateur.ROLES, new CodeReferentiel(code));
            if (!PORTEE_ORGANISATION.equals(role.attributs().get(PORTEE))) {
                throw new RoleNonAttribuable();
            }
        }
        return roles;
    }

    private IdentifiantOrganisation courante() {
        return organisation.courante().orElseThrow(OrganisationRequise::new);
    }

    private UtilisateurPortail trouver(UUID id) {
        courante();
        return utilisateurs.parId(id).orElseThrow(UtilisateurIntrouvable::new);
    }

    private void consigner(IdentifiantOrganisation org, UtilisateurPortail u, String action) {
        collaborateurs.audit().consigner(new EvenementAudit(collaborateurs.identifiants().suivant(), org, collaborateurs.acteur().courant(),
                action, u.id().toString(), collaborateurs.horloge().instant()));
    }
}
