package ca.vinaitsolution.vinasign.acces.domain;

import ca.vinaitsolution.vinasign.commun.domain.ErreurMetier;

public class TransitionUtilisateurInterdite extends ErreurMetier {

    public TransitionUtilisateurInterdite() {
        super(Nature.CONFLIT, "utilisateur-desactive", "Cet utilisateur est désactivé.");
    }
}
