package ca.vinaitsolution.vinasign.session.application;

import ca.vinaitsolution.vinasign.commun.application.dto.DossierEnveloppe;
import ca.vinaitsolution.vinasign.commun.application.port.AbonneEvenements;
import ca.vinaitsolution.vinasign.commun.application.port.LectureEnveloppes;
import ca.vinaitsolution.vinasign.commun.application.port.PublicationEvenements;
import ca.vinaitsolution.vinasign.commun.domain.EvenementMetier;
import ca.vinaitsolution.vinasign.session.application.port.LiensSignature;
import ca.vinaitsolution.vinasign.session.domain.LienSignature;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Émission des liens (VS-040) dès qu'un signataire invité peut agir : à l'envoi de l'enveloppe, puis à chaque tour. Un
 * signataire qui a déjà un lien actif n'en reçoit pas d'autre. Chaque lien est remis au contexte des notifications
 * (événement {@code invitation-a-envoyer}) ; le jeton en clair n'est conservé nulle part.
 */
public class EmettreLiens implements AbonneEvenements {

    static final String ENVOYEE = "enveloppe-envoyee";
    static final String INVITATION = "invitation-a-envoyer";

    private final LiensSignature liens;
    private final LectureEnveloppes enveloppes;
    private final PublicationEvenements publication;
    private final ServicesSession services;

    public EmettreLiens(LiensSignature liens, LectureEnveloppes enveloppes, PublicationEvenements publication, ServicesSession services) {
        this.liens = liens;
        this.enveloppes = enveloppes;
        this.publication = publication;
        this.services = services;
    }

    /** À l'envoi, puis après chaque signature : le tour suivant est invité. */
    @Override
    public boolean concerne(String type) {
        return ENVOYEE.equals(type) || "signataire-a-signe".equals(type);
    }

    @Override
    public void recevoir(EvenementMetier evenement) {
        emettre(UUID.fromString(evenement.attributs().get("enveloppe")));
    }

    /**
     * Relance (VS-082) : révoque le lien actif et en émet un nouveau, de rang {@code rang}, avec le modèle de courriel
     * de relance. Rend le jeton en clair.
     */
    public String relancer(UUID enveloppe, UUID signataire, UUID ancien, int rang) {
        DossierEnveloppe d = enveloppes.dossier(enveloppe).orElseThrow(() -> new IllegalStateException("Enveloppe introuvable."));
        Instant maintenant = services.horloge().instant();
        liens.revoquer(ancien, maintenant);
        return emettre(d, signataire, maintenant, rang, "relance");
    }

    private String emettre(DossierEnveloppe d, UUID signataire, Instant maintenant, int rang, String modele) {
        Instant expire = d.expireLe().isBefore(maintenant.plus(services.reglages().dureeLien())) ? d.expireLe()
                : maintenant.plus(services.reglages().dureeLien());
        String jeton = services.nouveauJeton();
        liens.ajouter(LienSignature.emettre(services.identifiants().suivant(), d.id(), signataire, ServicesSession.empreinte(jeton), expire,
                services.reglages().ouverturesMax()).relance(rang));
        publication.publier(new EvenementMetier(INVITATION, new ca.vinaitsolution.vinasign.commun.domain.IdentifiantOrganisation(d.organisation()),
                Map.of("enveloppe", d.id().toString(), "signataire", signataire.toString(), "jeton", jeton, "modele", modele)));
        return jeton;
    }

    /** Rend, pour chaque lien émis, l'identifiant du signataire et le jeton en clair (remis une seule fois). */
    public List<Map.Entry<UUID, String>> emettre(UUID enveloppe) {
        DossierEnveloppe d = enveloppes.dossier(enveloppe).orElseThrow(() -> new IllegalStateException("Enveloppe introuvable."));
        Instant maintenant = services.horloge().instant();
        List<Map.Entry<UUID, String>> emis = new ArrayList<>();
        for (DossierEnveloppe.SignataireDossier s : d.peuventAgir()) {
            if (s.courriel() != null && liens.actifs(s.id(), maintenant).isEmpty()) {
                emis.add(Map.entry(s.id(), emettre(d, s.id(), maintenant, 0, "invitation")));
            }
        }
        return emis;
    }
}
