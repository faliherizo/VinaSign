package ca.vinaitsolution.vinasign.acces.domain;

import ca.vinaitsolution.vinasign.commun.domain.ErreurMetier;

public class UtilisateurIntrouvable extends ErreurMetier {

    public UtilisateurIntrouvable() {
        super(Nature.INTROUVABLE, "utilisateur-introuvable", "Utilisateur introuvable dans cette organisation.");
    }
}
