package ca.vinaitsolution.vinasign.enveloppe.application;

import ca.vinaitsolution.vinasign.commun.application.port.PlanOrganisations;
import ca.vinaitsolution.vinasign.enveloppe.application.dto.VueEnveloppe;
import ca.vinaitsolution.vinasign.enveloppe.domain.Enveloppe;
import ca.vinaitsolution.vinasign.enveloppe.domain.IdentifiantEnveloppe;
import ca.vinaitsolution.vinasign.enveloppe.domain.ModeEnveloppe;
import ca.vinaitsolution.vinasign.enveloppe.domain.QuotaAtteint;
import ca.vinaitsolution.vinasign.referentiel.application.ExigerValeurReferentiel;
import ca.vinaitsolution.vinasign.referentiel.domain.CodeReferentiel;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;

/**
 * Création d'une enveloppe par une application cliente (VS-020), en brouillon. Le mode est validé contre le référentiel
 * {@code mode-enveloppe} ; le quota mensuel vient du plan de l'organisation (attribut {@code enveloppesParMois}).
 */
public class CreerEnveloppe {

    /** Demande de création. */
    public record Demande(String referenceExterne, String mode, Instant expireLe) {
    }

    static final CodeReferentiel MODES = new CodeReferentiel("mode-enveloppe");
    static final CodeReferentiel PLANS = new CodeReferentiel("plan");
    static final String QUOTA = "enveloppesParMois";

    private final ServicesEnveloppe services;
    private final ExigerValeurReferentiel referentiel;
    private final PlanOrganisations plans;

    public CreerEnveloppe(ServicesEnveloppe services, ExigerValeurReferentiel referentiel, PlanOrganisations plans) {
        this.services = services;
        this.referentiel = referentiel;
        this.plans = plans;
    }

    public VueEnveloppe executer(Demande demande) {
        ServicesEnveloppe.Appelant a = services.appelant();
        referentiel.executer(MODES, new CodeReferentiel(demande.mode()));
        Instant maintenant = services.horloge().instant();
        quota(a, maintenant).filter(q -> services.enveloppes().creeesDepuis(a.organisation(), debutDuMois(maintenant)) >= q)
                .ifPresent(q -> { throw new QuotaAtteint(); });
        Enveloppe e = Enveloppe.creer(new IdentifiantEnveloppe(services.identifiants().suivant()), a.organisation(), a.application(),
                demande.referenceExterne(), ModeEnveloppe.depuisCode(demande.mode()), demande.expireLe(), maintenant);
        services.enveloppes().ajouter(e);
        services.consigner(e, "enveloppe-creee", Map.of("mode", e.mode().code()));
        return VueEnveloppe.de(e, java.util.List.of());
    }

    private Optional<Long> quota(ServicesEnveloppe.Appelant a, Instant maintenant) {
        return plans.plan(a.organisation())
                .map(p -> referentiel.executer(PLANS, new CodeReferentiel(p)).attributs().get(QUOTA))
                .map(Long::valueOf);
    }

    private static Instant debutDuMois(Instant maintenant) {
        return LocalDate.ofInstant(maintenant, ZoneOffset.UTC).withDayOfMonth(1).atStartOfDay().toInstant(ZoneOffset.UTC);
    }
}
