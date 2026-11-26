package ca.vinaitsolution.vinasign.acces.application;

import ca.vinaitsolution.vinasign.commun.application.port.AbonneEvenements;
import ca.vinaitsolution.vinasign.commun.domain.EvenementMetier;

/** Organisation née d'une inscription (VS-122) : son demandeur en devient l'administrateur, invité au portail. */
public class InviterAdministrateurInscrit implements AbonneEvenements {

    private final GererUtilisateurs utilisateurs;

    public InviterAdministrateurInscrit(GererUtilisateurs utilisateurs) {
        this.utilisateurs = utilisateurs;
    }

    @Override
    public boolean concerne(String type) {
        return "organisation-inscrite".equals(type);
    }

    @Override
    public void recevoir(EvenementMetier e) {
        utilisateurs.inviterAdministrateur(e.organisation(), e.attributs().get("courriel"));
    }
}
