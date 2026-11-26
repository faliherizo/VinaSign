package ca.vinaitsolution.vinasign.notification.application;

import ca.vinaitsolution.vinasign.commun.application.port.AbonneEvenements;
import ca.vinaitsolution.vinasign.commun.application.port.GenerateurIdentifiant;
import ca.vinaitsolution.vinasign.commun.domain.EvenementMetier;
import ca.vinaitsolution.vinasign.notification.application.port.BoiteEnvoi;
import ca.vinaitsolution.vinasign.notification.domain.CourrielSortant;
import java.time.Clock;
import java.util.Map;

/**
 * Courriel de confirmation d'une inscription (VS-122), dans la langue du demandeur, rattaché à l'identifiant de la demande
 * (celui de la future organisation). Le lien porte le jeton : le corps est effacé une fois le courriel envoyé.
 */
public class ConfirmerInscription implements AbonneEvenements {

    private final BoiteEnvoi boite;
    private final ModelesCourriel modeles;
    private final GenerateurIdentifiant identifiants;
    private final Clock horloge;

    public ConfirmerInscription(BoiteEnvoi boite, ModelesCourriel modeles, GenerateurIdentifiant identifiants, Clock horloge) {
        this.boite = boite;
        this.modeles = modeles;
        this.identifiants = identifiants;
        this.horloge = horloge;
    }

    @Override
    public boolean concerne(String type) {
        return "inscription-a-confirmer".equals(type);
    }

    @Override
    public void recevoir(EvenementMetier e) {
        Map<String, String> a = e.attributs();
        ModelesCourriel.Rendu r = modeles.rendre("inscription", a.get("langue"), Map.of("organisation", a.get("organisation"), "lien", a.get("lien")));
        boite.deposer(new CourrielSortant(identifiants.suivant(), e.organisation().valeur(), a.get("courriel"), r.sujet(), r.corps(), 0, horloge.instant()));
    }
}
