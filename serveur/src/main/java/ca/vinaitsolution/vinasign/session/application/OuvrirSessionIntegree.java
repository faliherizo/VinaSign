package ca.vinaitsolution.vinasign.session.application;

import ca.vinaitsolution.vinasign.commun.application.dto.DossierEnveloppe;
import ca.vinaitsolution.vinasign.commun.application.port.ContexteAccesCourant;
import ca.vinaitsolution.vinasign.commun.application.port.LectureEnveloppes;
import ca.vinaitsolution.vinasign.commun.domain.ContexteAcces;
import ca.vinaitsolution.vinasign.referentiel.application.ExigerValeurReferentiel;
import ca.vinaitsolution.vinasign.referentiel.domain.CodeReferentiel;
import ca.vinaitsolution.vinasign.session.application.port.LiensSignature;
import ca.vinaitsolution.vinasign.session.domain.LienInvalide;
import ca.vinaitsolution.vinasign.session.domain.LienSignature;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import ca.vinaitsolution.vinasign.commun.application.port.VerificateurAttestations;
import ca.vinaitsolution.vinasign.commun.domain.Attestation;
import ca.vinaitsolution.vinasign.session.application.port.AttestationsLiens;
import ca.vinaitsolution.vinasign.session.domain.AttestationRetenue;
import ca.vinaitsolution.vinasign.session.domain.ExigenceAttestation;
import java.util.UUID;

/**
 * Session intégrée (VS-046) : l'application cliente obtient, pour un signataire de son enveloppe dont c'est le tour, une
 * adresse de l'interface du signataire valable 5 minutes, à usage unique, limitée aux actions demandées.
 */
public class OuvrirSessionIntegree {

    /** Adresse à intégrer (iframe) et échéance. */
    public record Integration(String url, Instant expireLe) {
    }

    static final CodeReferentiel ACTIONS = new CodeReferentiel("action-signataire");
    static final CodeReferentiel NIVEAUX = new CodeReferentiel("niveau-authentification");
    static final CodeReferentiel TYPES = new CodeReferentiel("type-signataire");

    /** Attestation de l'utilisateur (VS-044) : assertion signée par l'application, niveau demandé (null : niveau par défaut). */
    public record Attestee(String assertion, String niveau) {
    }

    /** Vérification et règles de l'attestation, et niveau retenu quand aucun n'est demandé. */
    public record Attestations(VerificateurAttestations verificateur, ExigenceAttestation exigence, AttestationsLiens retenues, String niveauParDefaut) {
    }

    private final LiensSignature liens;
    private final LectureEnveloppes enveloppes;
    private final ContexteAccesCourant acces;
    private final ExigerValeurReferentiel referentiel;
    private final ServicesSession services;
    private final Duration duree;
    private final String urlSignataire;
    private final Attestations attestations;

    public OuvrirSessionIntegree(LiensSignature liens, LectureEnveloppes enveloppes, ContexteAccesCourant acces, ExigerValeurReferentiel referentiel,
                                 ServicesSession services, Duration duree, String urlSignataire, Attestations attestations) {
        this.attestations = attestations;
        this.liens = liens;
        this.enveloppes = enveloppes;
        this.acces = acces;
        this.referentiel = referentiel;
        this.services = services;
        this.duree = duree;
        this.urlSignataire = urlSignataire.replaceAll("/+$", "");
    }

    public Integration executer(UUID enveloppe, UUID signataire, Set<String> actions, Attestee attestee) {
        if (actions == null || actions.isEmpty()) {
            throw new IllegalArgumentException("Au moins une action est requise.");
        }
        actions.forEach(a -> referentiel.executer(ACTIONS, new CodeReferentiel(a)));
        ContexteAcces c = acces.courant();
        DossierEnveloppe d = enveloppes.dossier(enveloppe).filter(x -> c.client().map(x.application()::equals).orElse(false))
                .orElseThrow(LienInvalide::new);
        DossierEnveloppe.SignataireDossier s = d.peuventAgir().stream().filter(x -> x.id().equals(signataire)).findFirst()
                .orElseThrow(ca.vinaitsolution.vinasign.session.domain.SignataireHorsTour::new);
        Optional<Accordee> accordee = attester(d.application(), s, attestee);
        String jeton = services.nouveauJeton();
        Instant expire = services.horloge().instant().plus(duree);
        UUID lien = services.identifiants().suivant();
        liens.ajouter(LienSignature.integree(lien, enveloppe, signataire, ServicesSession.empreinte(jeton), expire, actions));
        accordee.ifPresent(r -> attestations.retenues().ajouter(new AttestationRetenue(lien, r.attestation().sujet(), r.attestation().methodes(),
                r.attestation().authentifieLe(), r.niveau(), r.le())));
        return new Integration(urlSignataire + "/integre/" + jeton, expire);
    }

    /**
     * Un signataire dont le type exige une attestation (attribut {@code exigeAttestation}) n'a de session qu'attestée par
     * l'application, au niveau demandé ; pour les autres, aucune attestation n'est retenue.
     */
    private record Accordee(Attestation attestation, String niveau, Instant le) {
    }

    private Optional<Accordee> attester(String application, DossierEnveloppe.SignataireDossier s, Attestee attestee) {
        if (!Boolean.parseBoolean(referentiel.executer(TYPES, new CodeReferentiel(s.type())).attributs().get("exigeAttestation"))) {
            return Optional.empty();
        }
        String niveau = attestee == null || attestee.niveau() == null ? attestations.niveauParDefaut() : attestee.niveau();
        int rang = Integer.parseInt(referentiel.executer(NIVEAUX, new CodeReferentiel(niveau)).attributs().getOrDefault("rang", "1"));
        Attestation a = attestations.verificateur().verifier(application, attestee == null ? null : attestee.assertion());
        Instant maintenant = services.horloge().instant();
        attestations.exigence().verifier(a, s.sujetExterne(), rang, maintenant);
        return Optional.of(new Accordee(a, niveau, maintenant));
    }
}
