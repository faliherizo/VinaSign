package ca.vinaitsolution.vinasign.commun.application;

import ca.vinaitsolution.vinasign.commun.application.port.PlanOrganisations;
import ca.vinaitsolution.vinasign.commun.domain.ContexteAcces;
import ca.vinaitsolution.vinasign.commun.domain.IdentifiantOrganisation;
import ca.vinaitsolution.vinasign.commun.domain.SeauJetons;
import ca.vinaitsolution.vinasign.referentiel.application.port.Referentiels;
import ca.vinaitsolution.vinasign.referentiel.domain.CodeReferentiel;
import ca.vinaitsolution.vinasign.referentiel.domain.TypeReferentiel;
import java.time.Clock;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Limitation du débit (VS-015) : un seau par demandeur (application cliente, utilisateur, ou adresse pour une requête
 * anonyme), dont la capacité par minute vient du plan de son organisation (attribut {@code requetesParMinute} du
 * référentiel {@code plan}). Les seaux vivent dans chaque réplique : la limite effective d'une plateforme à N répliques
 * peut atteindre N fois celle du plan ; la passerelle (WAF) porte la limite globale.
 */
public class LimiterDebit {

    /** Demande de passage. {@code demandeur} : clé du seau ; {@code contexte} : ce qu'elle engage. */
    public record Demande(String demandeur, ContexteAcces contexte) {
    }

    /** Réponse : passage accordé, ou délai à respecter. */
    public record Reponse(boolean acceptee, Duration attente) {
    }

    static final CodeReferentiel PLANS = new CodeReferentiel("plan");
    static final String DEBIT = "requetesParMinute";

    private final PlanOrganisations plans;
    private final Referentiels referentiels;
    private final int debitAnonyme;
    private final Clock horloge;
    private final Map<String, SeauJetons> seaux = new ConcurrentHashMap<>();

    public LimiterDebit(PlanOrganisations plans, Referentiels referentiels, int debitAnonyme, Clock horloge) {
        this.plans = plans;
        this.referentiels = referentiels;
        this.debitAnonyme = debitAnonyme;
        this.horloge = horloge;
    }

    public Reponse passer(Demande demande) {
        int capacite = demande.contexte().organisation().flatMap(this::debitDuPlan).orElse(debitAnonyme);
        SeauJetons.Decision[] decision = new SeauJetons.Decision[1];
        seaux.compute(demande.demandeur(), (cle, seau) -> {
            SeauJetons courant = seau == null || seau.capacite() != capacite ? SeauJetons.plein(capacite, horloge.instant()) : seau;
            decision[0] = courant.tenter(horloge.instant());
            return decision[0].seau();
        });
        return new Reponse(decision[0].acceptee(), decision[0].attente());
    }

    /** Oublie les seaux pleins : leur état ne change aucune décision ; borne la mémoire. Rend le nombre oublié. */
    public int oublierInactifs() {
        int avant = seaux.size();
        seaux.values().removeIf(s -> s.estPleinA(horloge.instant()));
        return avant - seaux.size();
    }

    private Optional<Integer> debitDuPlan(IdentifiantOrganisation organisation) {
        Optional<TypeReferentiel> type = referentiels.type(PLANS);
        return plans.plan(organisation)
                .flatMap(code -> type.flatMap(t -> t.valeurActive(new CodeReferentiel(code))))
                .map(v -> v.attributs().get(DEBIT))
                .map(Integer::valueOf);
    }
}
