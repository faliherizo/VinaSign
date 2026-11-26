package ca.vinaitsolution.vinasign.facturation.application;

import ca.vinaitsolution.vinasign.commun.application.port.ExecutionOrganisation;
import ca.vinaitsolution.vinasign.commun.application.port.GenerateurIdentifiant;
import ca.vinaitsolution.vinasign.commun.application.port.OrganisationCourante;
import ca.vinaitsolution.vinasign.commun.application.port.SouscriptionPlans;
import ca.vinaitsolution.vinasign.commun.domain.IdentifiantOrganisation;
import ca.vinaitsolution.vinasign.facturation.application.port.Abonnements;
import ca.vinaitsolution.vinasign.facturation.application.port.EvenementsPaiement;
import ca.vinaitsolution.vinasign.facturation.application.port.Factures;
import ca.vinaitsolution.vinasign.facturation.application.port.PrestatairePaiement;
import ca.vinaitsolution.vinasign.facturation.domain.Abonnement;
import ca.vinaitsolution.vinasign.facturation.domain.AbonnementIntrouvable;
import ca.vinaitsolution.vinasign.facturation.domain.EtatAbonnement;
import ca.vinaitsolution.vinasign.facturation.domain.EvenementPaiement;
import ca.vinaitsolution.vinasign.facturation.domain.Facture;
import ca.vinaitsolution.vinasign.referentiel.application.ExigerValeurReferentiel;
import ca.vinaitsolution.vinasign.referentiel.domain.CodeReferentiel;
import java.time.Clock;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Souscription et paiement d'un plan (VS-121). L'administrateur de l'organisation est envoyé sur la page de paiement
 * hébergée du prestataire ; les événements signés du prestataire, traités une seule fois, tiennent l'abonnement, le plan
 * de l'organisation et ses factures à jour. Aucun numéro de carte ne transite par VinaSign.
 */
public class GererAbonnement {

    static final String ACTEUR = "plateforme:facturation";
    static final CodeReferentiel PLANS = new CodeReferentiel("plan");

    /** Abonnement et factures de l'organisation. */
    public record Situation(Optional<Abonnement> abonnement, List<Facture> factures) {
    }

    /** Dépôts : abonnements, factures, événements déjà traités, plans des organisations. */
    public record Depots(Abonnements abonnements, Factures factures, EvenementsPaiement evenements, SouscriptionPlans plans) {
    }

    /** Contexte : organisation courante, exécution au nom d'une organisation, référentiels, identifiants et horloge. */
    public record Contexte(OrganisationCourante organisation, ExecutionOrganisation execution, ExigerValeurReferentiel referentiel,
                           GenerateurIdentifiant identifiants, Clock horloge) {
    }

    private final PrestatairePaiement prestataire;
    private final Depots depots;
    private final Contexte contexte;

    public GererAbonnement(PrestatairePaiement prestataire, Depots depots, Contexte contexte) {
        this.prestataire = prestataire;
        this.depots = depots;
        this.contexte = contexte;
    }

    public Situation consulter() {
        UUID org = courante();
        return new Situation(depots.abonnements().de(org), depots.factures().deLOrganisation(org));
    }

    /** Page de paiement du plan choisi (valeur active du référentiel plan). */
    public String payer(String plan, String langue) {
        contexte.referentiel().executer(PLANS, new CodeReferentiel(plan));
        UUID org = courante();
        String client = depots.abonnements().de(org).map(Abonnement::client).orElse(null);
        return prestataire.ouvrirPaiement(new PrestatairePaiement.Souscription(org, plan, client, langue));
    }

    public String gerer() {
        return prestataire.ouvrirGestion(depots.abonnements().de(courante()).orElseThrow(AbonnementIntrouvable::new).client());
    }

    /** Événement du prestataire, hors organisation : authentifié, puis appliqué une seule fois dans l'organisation concernée. */
    public void recevoir(byte[] corps, String signature) {
        EvenementPaiement e = prestataire.lire(corps, signature);
        Optional<UUID> org = switch (e) {
            case EvenementPaiement.PaiementConfirme p -> Optional.of(p.organisation());
            case EvenementPaiement.AbonnementModifie m -> depots.abonnements().organisationDuClient(m.client());
            case EvenementPaiement.FacturePayee f -> depots.abonnements().organisationDuClient(f.client());
            case EvenementPaiement.Ignore i -> Optional.empty();
        };
        org.ifPresent(o -> contexte.execution().pour(new IdentifiantOrganisation(o), ACTEUR, () -> {
            if (depots.evenements().retenir(e.id(), contexte.horloge().instant())) {
                appliquer(o, e);
            }
            return null;
        }));
    }

    private void appliquer(UUID org, EvenementPaiement e) {
        switch (e) {
            case EvenementPaiement.PaiementConfirme p -> suivre(depots.abonnements().de(org)
                    .map(a -> new Abonnement(org, a.prestataire(), p.client(), p.reference(), p.plan(), EtatAbonnement.ACTIF, contexte.horloge().instant()))
                    .orElseGet(() -> new Abonnement(org, prestataire.code(), p.client(), p.reference(), p.plan(), EtatAbonnement.ACTIF,
                            contexte.horloge().instant())));
            case EvenementPaiement.AbonnementModifie m -> depots.abonnements().de(org)
                    .ifPresent(a -> suivre(a.evoluer(m.reference(), m.plan(), m.etat(), contexte.horloge().instant())));
            case EvenementPaiement.FacturePayee f -> depots.factures().ajouter(new Facture(contexte.identifiants().suivant(), org, f.reference(),
                    f.numero(), f.montant(), f.devise(), f.urlHebergee(), f.urlPdf(), f.debutPeriode(), f.finPeriode(), f.payeeLe()));
            case EvenementPaiement.Ignore i -> { }
        }
    }

    private void suivre(Abonnement a) {
        depots.abonnements().enregistrer(a);
        IdentifiantOrganisation org = new IdentifiantOrganisation(a.organisation());
        switch (a.effetPlan()) {
            case APPLIQUER -> depots.plans().appliquer(org, a.plan());
            case PAR_DEFAUT -> depots.plans().revenirAuPlanParDefaut(org);
            case AUCUN -> { }
        }
    }

    private UUID courante() {
        return contexte.organisation().courante().orElseThrow(AbonnementIntrouvable::new).valeur();
    }
}
