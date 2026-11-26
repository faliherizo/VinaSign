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
 * Confirmation aux signataires (VS-080) : une fois l'enveloppe complétée (VS-070), chaque signataire joignable par
 * courriel en est avisé dans sa langue. Aucune pièce jointe : le document signé est transmis par l'expéditeur.
 */
public class ConfirmerParCourriel implements AbonneEvenements {

    static final String COMPLETEE = "enveloppe-completee";
    static final String MODELE = "confirmation";

    private final BoiteEnvoi boite;
    private final LectureEnveloppes enveloppes;
    private final ModelesCourriel modeles;
    private final GenerateurIdentifiant identifiants;
    private final Clock horloge;

    public ConfirmerParCourriel(BoiteEnvoi boite, LectureEnveloppes enveloppes, ModelesCourriel modeles, GenerateurIdentifiant identifiants, Clock horloge) {
        this.boite = boite;
        this.enveloppes = enveloppes;
        this.modeles = modeles;
        this.identifiants = identifiants;
        this.horloge = horloge;
    }

    @Override
    public boolean concerne(String type) {
        return COMPLETEE.equals(type);
    }

    @Override
    public void recevoir(EvenementMetier e) {
        DossierEnveloppe d = enveloppes.dossier(UUID.fromString(e.attributs().get("enveloppe")))
                .orElseThrow(() -> new IllegalStateException("Enveloppe introuvable."));
        for (DossierEnveloppe.SignataireDossier s : d.signataires()) {
            if (s.courriel() == null) {
                continue;
            }
            ModelesCourriel.Rendu r = modeles.rendre(MODELE, s.langue(), Map.of("nom", s.nom(), "reference", d.referenceExterne()));
            boite.deposer(new CourrielSortant(identifiants.suivant(), d.organisation(), s.courriel(), r.sujet(), r.corps(), 0, horloge.instant(), d.id()));
        }
    }
}
