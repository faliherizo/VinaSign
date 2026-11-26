package ca.vinaitsolution.vinasign.session.domain;

import ca.vinaitsolution.vinasign.commun.domain.ErreurMetier;

/** Lien inconnu, expiré, révoqué ou épuisé : une seule réponse, pour ne rien révéler. */
public class LienInvalide extends ErreurMetier {

    public LienInvalide() {
        super(Nature.INTROUVABLE, "lien-invalide", "Ce lien n'est pas valide ou a expiré. Demander un nouveau lien à l'expéditeur.");
    }
}
