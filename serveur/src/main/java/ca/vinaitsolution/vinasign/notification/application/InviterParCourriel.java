package ca.vinaitsolution.vinasign.notification.application;

import ca.vinaitsolution.vinasign.commun.application.dto.DossierEnveloppe;
import ca.vinaitsolution.vinasign.commun.application.port.AbonneEvenements;
import ca.vinaitsolution.vinasign.commun.application.port.GenerateurIdentifiant;
import ca.vinaitsolution.vinasign.commun.application.port.LectureEnveloppes;
import ca.vinaitsolution.vinasign.commun.domain.EvenementMetier;
import ca.vinaitsolution.vinasign.notification.application.port.BoiteEnvoi;
import ca.vinaitsolution.vinasign.notification.domain.CourrielSortant;
import java.time.Clock;
import java.util.Map;
import java.util.UUID;

/**
 * Invitation par courriel (VS-080) : à chaque lien émis, un courriel dans la langue du signataire, avec le lien de
 * l'interface du signataire ; aucun document joint.
 */
public class InviterParCourriel implements AbonneEvenements {

    static final String INVITATION = "invitation-a-envoyer";
    static final String MODELE = "invitation";

    private final BoiteEnvoi boite;
    private final LectureEnveloppes enveloppes;
    private final ModelesCourriel modeles;
    private final GenerateurIdentifiant identifiants;
    private final Clock horloge;
    private final String urlSignataire;

    public InviterParCourriel(BoiteEnvoi boite, LectureEnveloppes enveloppes, ModelesCourriel modeles, GenerateurIdentifiant identifiants,
                              Clock horloge, String urlSignataire) {
        this.boite = boite;
        this.enveloppes = enveloppes;
        this.modeles = modeles;
        this.identifiants = identifiants;
        this.horloge = horloge;
        this.urlSignataire = urlSignataire.replaceAll("/+$", "");
    }

    @Override
    public boolean concerne(String type) {
        return INVITATION.equals(type);
    }

    @Override
    public void recevoir(EvenementMetier e) {
        UUID enveloppe = UUID.fromString(e.attributs().get("enveloppe"));
        UUID signataire = UUID.fromString(e.attributs().get("signataire"));
        DossierEnveloppe d = enveloppes.dossier(enveloppe).orElseThrow(() -> new IllegalStateException("Enveloppe introuvable."));
        DossierEnveloppe.SignataireDossier s = d.signataires().stream().filter(x -> x.id().equals(signataire)).findFirst()
                .orElseThrow(() -> new IllegalStateException("Signataire introuvable."));
        ModelesCourriel.Rendu r = modeles.rendre(e.attributs().getOrDefault("modele", MODELE), s.langue(), Map.of("nom", s.nom(), "lien", urlSignataire + "/s/" + e.attributs().get("jeton"),
                "echeance", d.expireLe().toString(), "reference", d.referenceExterne()));
        boite.deposer(new CourrielSortant(identifiants.suivant(), d.organisation(), s.courriel(), r.sujet(), r.corps(), 0, horloge.instant(), d.id()));
    }
}
