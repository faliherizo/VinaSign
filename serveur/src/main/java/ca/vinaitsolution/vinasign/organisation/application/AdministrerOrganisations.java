package ca.vinaitsolution.vinasign.organisation.application;

import ca.vinaitsolution.vinasign.commun.application.port.ActeurCourant;
import ca.vinaitsolution.vinasign.commun.application.port.GenerateurIdentifiant;
import ca.vinaitsolution.vinasign.commun.application.port.JournalAudit;
import ca.vinaitsolution.vinasign.commun.domain.EvenementAudit;
import ca.vinaitsolution.vinasign.commun.domain.IdentifiantOrganisation;
import ca.vinaitsolution.vinasign.organisation.application.dto.VueOrganisation;
import ca.vinaitsolution.vinasign.organisation.application.port.Organisations;
import ca.vinaitsolution.vinasign.organisation.domain.Organisation;
import ca.vinaitsolution.vinasign.organisation.domain.OrganisationIntrouvable;
import ca.vinaitsolution.vinasign.referentiel.application.ExigerValeurReferentiel;
import ca.vinaitsolution.vinasign.referentiel.application.port.Referentiels;
import ca.vinaitsolution.vinasign.referentiel.domain.CodeReferentiel;
import ca.vinaitsolution.vinasign.referentiel.domain.TypeReferentiel;
import ca.vinaitsolution.vinasign.referentiel.domain.ValeurReferentiel;
import java.time.Clock;
import java.util.List;
import java.util.function.BiFunction;

/**
 * Administration des organisations par la plateforme (VS-010) : création, suspension, réactivation, chacune consignée
 * au journal d'audit avec son acteur. L'autorisation (rôle d'administrateur de la plateforme) est vérifiée à l'entrée.
 */
public class AdministrerOrganisations {

    static final String CREEE = "organisation-creee";
    static final String SUSPENDUE = "organisation-suspendue";
    static final String REACTIVEE = "organisation-reactivee";
    static final String PLAN_CHANGE = "plan-organisation-change";
    static final CodeReferentiel PLANS = new CodeReferentiel("plan");
    static final String PAR_DEFAUT = "parDefaut";

    private final Organisations organisations;
    private final Referentiels referentiels;
    private final JournalAudit audit;
    private final ActeurCourant acteur;
    private final GenerateurIdentifiant identifiants;
    private final Clock horloge;

    public AdministrerOrganisations(Organisations organisations, Referentiels referentiels, JournalAudit audit, ActeurCourant acteur,
                                    GenerateurIdentifiant identifiants, Clock horloge) {
        this.organisations = organisations;
        this.referentiels = referentiels;
        this.audit = audit;
        this.acteur = acteur;
        this.identifiants = identifiants;
        this.horloge = horloge;
    }

    public VueOrganisation creer(String nom) {
        Organisation o = Organisation.creer(new IdentifiantOrganisation(identifiants.suivant()), nom, planParDefaut(), horloge.instant());
        organisations.ajouter(o);
        consigner(o, CREEE);
        return VueOrganisation.de(o);
    }

    /** Organisation née d'une inscription acceptée (VS-122) : elle reprend l'identifiant de la demande. */
    public VueOrganisation inscrire(IdentifiantOrganisation id, String nom) {
        Organisation o = Organisation.creer(id, nom, planParDefaut(), horloge.instant());
        organisations.ajouter(o);
        consigner(o, CREEE);
        return VueOrganisation.de(o);
    }

    public VueOrganisation consulter(IdentifiantOrganisation id) {
        return VueOrganisation.de(trouver(id));
    }

    public VueOrganisation suspendre(IdentifiantOrganisation id) {
        return transition(id, Organisation::suspendre, SUSPENDUE);
    }

    public VueOrganisation reactiver(IdentifiantOrganisation id) {
        return transition(id, Organisation::reactiver, REACTIVEE);
    }

    /** Le plan doit être une valeur active du référentiel {@code plan}. */
    public VueOrganisation changerPlan(IdentifiantOrganisation id, String plan) {
        new ExigerValeurReferentiel(referentiels).executer(PLANS, new CodeReferentiel(plan));
        return transition(id, (o, le) -> o.changerPlan(plan, le), PLAN_CHANGE);
    }

    /** Fin d'un abonnement payé (VS-121) : retour au plan par défaut ; sans effet s'il est déjà le plan de l'organisation. */
    public VueOrganisation revenirAuPlanParDefaut(IdentifiantOrganisation id) {
        return appliquerPlan(id, planParDefaut());
    }

    /** Plan payé (VS-121) ; sans effet ni audit s'il est déjà celui de l'organisation (événement du prestataire répété). */
    public VueOrganisation appliquerPlan(IdentifiantOrganisation id, String plan) {
        Organisation o = trouver(id);
        return plan.equals(o.plan()) ? VueOrganisation.de(o) : changerPlan(id, plan);
    }

    /** Plan d'une nouvelle organisation : la valeur active marquée {@code parDefaut}, sinon la première active. */
    private String planParDefaut() {
        List<ValeurReferentiel> actifs = referentiels.type(PLANS).map(TypeReferentiel::valeursActives).orElse(List.of());
        return actifs.stream().filter(v -> "true".equals(v.attributs().get(PAR_DEFAUT))).findFirst()
                .or(() -> actifs.stream().findFirst())
                .map(v -> v.code().valeur())
                .orElseThrow(() -> new IllegalStateException("Aucun plan actif dans le référentiel plan."));
    }

    private VueOrganisation transition(IdentifiantOrganisation id, BiFunction<Organisation, java.time.Instant, Organisation> changement,
                                       String action) {
        Organisation suivante = changement.apply(trouver(id), horloge.instant());
        organisations.remplacer(suivante);
        consigner(suivante, action);
        return VueOrganisation.de(suivante);
    }

    private Organisation trouver(IdentifiantOrganisation id) {
        return organisations.parId(id).orElseThrow(OrganisationIntrouvable::new);
    }

    private void consigner(Organisation o, String action) {
        audit.consigner(new EvenementAudit(identifiants.suivant(), o.id(), acteur.courant(), action, o.id().valeur().toString(),
                o.modifieLe()));
    }
}
