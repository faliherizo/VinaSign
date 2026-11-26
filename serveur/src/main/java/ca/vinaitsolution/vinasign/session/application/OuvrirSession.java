package ca.vinaitsolution.vinasign.session.application;

import ca.vinaitsolution.vinasign.commun.application.dto.DossierEnveloppe;
import ca.vinaitsolution.vinasign.commun.application.port.LectureEnveloppes;
import ca.vinaitsolution.vinasign.commun.application.port.LectureMarque;
import ca.vinaitsolution.vinasign.commun.application.port.OriginesIntegration;
import ca.vinaitsolution.vinasign.session.application.port.AttestationsLiens;
import ca.vinaitsolution.vinasign.session.application.port.DefisCode;
import ca.vinaitsolution.vinasign.session.application.dto.VueSession;
import ca.vinaitsolution.vinasign.session.application.port.Consentements;
import ca.vinaitsolution.vinasign.session.application.port.LiensSignature;
import ca.vinaitsolution.vinasign.session.application.port.SessionsSignataire;
import ca.vinaitsolution.vinasign.session.domain.LienInvalide;
import ca.vinaitsolution.vinasign.session.domain.LienResolu;
import ca.vinaitsolution.vinasign.session.domain.LienSignature;
import ca.vinaitsolution.vinasign.session.domain.SessionSignataire;
import java.time.Instant;
import java.util.UUID;

/**
 * Ouverture d'un lien (VS-040), en deux temps : {@link #resoudre} retrouve le lien par son jeton (aucune organisation
 * n'est encore connue) ; {@link #ouvrir}, une fois le signataire authentifié dans l'organisation de l'enveloppe, compte
 * l'ouverture et crée la session.
 */
public class OuvrirSession {

    private final LiensSignature liens;
    private final SessionsSignataire sessions;
    private final LectureEnveloppes enveloppes;
    private final TexteConsentement textes;
    private final Consentements consentements;
    private final ServicesSession services;
    private final OriginesIntegration origines;
    private final AttestationsLiens attestations;
    private final DefisCode niveaux;
    private final LectureMarque marques;

    /** Ce que l'ouverture lit au-delà du lien : origines d'intégration, attestation retenue, niveau de la session, marque. */
    public record Integration(OriginesIntegration origines, AttestationsLiens attestations, DefisCode niveaux, LectureMarque marques) {
    }

    public OuvrirSession(LiensSignature liens, SessionsSignataire sessions, LectureEnveloppes enveloppes, TexteConsentement textes,
                         Consentements consentements, ServicesSession services, Integration integration) {
        this.origines = integration.origines();
        this.attestations = integration.attestations();
        this.niveaux = integration.niveaux();
        this.marques = integration.marques();
        this.liens = liens;
        this.sessions = sessions;
        this.enveloppes = enveloppes;
        this.textes = textes;
        this.consentements = consentements;
        this.services = services;
    }

    /** Lien de ce jeton ; un jeton inconnu est refusé comme un lien invalide. */
    public LienResolu resoudre(String jeton) {
        if (jeton == null || jeton.isBlank() || jeton.length() > 100) {
            throw new LienInvalide();
        }
        return liens.parEmpreinte(ServicesSession.empreinte(jeton)).orElseThrow(LienInvalide::new);
    }

    public VueSession ouvrir(LienResolu resolu) {
        Instant maintenant = services.horloge().instant();
        LienSignature ouvert = resolu.lien().ouvrir(maintenant);
        liens.remplacer(ouvert);
        DossierEnveloppe d = enveloppes.dossier(ouvert.enveloppe()).orElseThrow(LienInvalide::new);
        DossierEnveloppe.SignataireDossier s = d.signataires().stream().filter(x -> x.id().equals(ouvert.signataire())).findFirst()
                .orElseThrow(LienInvalide::new);
        String jeton = services.nouveauJeton();
        Instant expire = maintenant.plus(services.reglages().dureeSession());
        UUID session = services.identifiants().suivant();
        sessions.ajouter(new SessionSignataire(session, resolu.organisation(), d.id(), s.id(), ServicesSession.empreinte(jeton), expire));
        // L'application a attesté son utilisateur (VS-044) : la session part du niveau accordé, consigné dans la preuve.
        String niveauAtteint = attestations.duLien(ouvert.id()).map(a -> {
            niveaux.elever(session, a.niveau());
            return a.niveau();
        }).orElse(null);
        TexteConsentement.Texte texte = textes.enVigueur(s.langue());
        boolean aConsenti = consentements.dernier(s.id()).map(c -> c.versionTexte().equals(texte.version())).orElse(false);
        return new VueSession(jeton, expire, d.id(), d.mode(), d.statut(), s.nom(), s.langue(), s.peutAgir(), aConsenti, texte.version(),
                texte.texte(), ouvert.actions().stream().sorted().toList(),
                ouvert.estIntegree() ? origines.de(d.application()).stream().sorted().toList() : java.util.List.of(), s.niveauExige(), niveauAtteint,
                marques.deOrganisation(resolu.organisation()).orElse(null));
    }
}
