package ca.vinaitsolution.vinasign.session.application;

import ca.vinaitsolution.vinasign.commun.application.dto.DossierEnveloppe;
import ca.vinaitsolution.vinasign.commun.application.port.LectureEnveloppes;
import ca.vinaitsolution.vinasign.session.application.port.Consentements;
import ca.vinaitsolution.vinasign.session.domain.Consentement;
import ca.vinaitsolution.vinasign.session.domain.ConsentementRequis;
import ca.vinaitsolution.vinasign.session.domain.LienInvalide;
import ca.vinaitsolution.vinasign.session.domain.VersionConsentementPerimee;
import java.util.UUID;

/**
 * Consentement explicite (VS-041) : le signataire accepte la version en vigueur du texte affiché dans sa langue ; on
 * conserve la version, l'empreinte du texte et l'heure. {@link #exiger} protège toute signature.
 */
public class Consentir {

    private final Consentements consentements;
    private final LectureEnveloppes enveloppes;
    private final TexteConsentement textes;
    private final ServicesSession services;

    public Consentir(Consentements consentements, LectureEnveloppes enveloppes, TexteConsentement textes, ServicesSession services) {
        this.consentements = consentements;
        this.enveloppes = enveloppes;
        this.textes = textes;
        this.services = services;
    }

    public Consentement executer(UUID enveloppe, UUID signataire, String versionAcceptee) {
        DossierEnveloppe.SignataireDossier s = signataire(enveloppe, signataire);
        TexteConsentement.Texte texte = textes.enVigueur(s.langue());
        if (!texte.version().equals(versionAcceptee)) {
            throw new VersionConsentementPerimee();
        }
        Consentement c = new Consentement(services.identifiants().suivant(), enveloppe, signataire, texte.version(),
                ServicesSession.empreinte(texte.texte()), services.horloge().instant());
        consentements.ajouter(c);
        return c;
    }

    /** Refuse toute action de signature sans consentement à la version en vigueur. */
    public void exiger(UUID enveloppe, UUID signataire) {
        String version = textes.enVigueur(signataire(enveloppe, signataire).langue()).version();
        if (consentements.dernier(signataire).filter(c -> c.versionTexte().equals(version)).isEmpty()) {
            throw new ConsentementRequis();
        }
    }

    private DossierEnveloppe.SignataireDossier signataire(UUID enveloppe, UUID signataire) {
        return enveloppes.dossier(enveloppe).flatMap(d -> d.signataires().stream().filter(x -> x.id().equals(signataire)).findFirst())
                .orElseThrow(LienInvalide::new);
    }
}
