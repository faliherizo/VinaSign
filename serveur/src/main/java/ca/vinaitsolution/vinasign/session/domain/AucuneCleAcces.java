package ca.vinaitsolution.vinasign.session.domain;

import ca.vinaitsolution.vinasign.commun.domain.ErreurMetier;

/** Le signataire n'a pas de clé d'accès : repli sur le code (VS-042). */
public class AucuneCleAcces extends ErreurMetier {

    public AucuneCleAcces() {
        super(Nature.INTROUVABLE, "aucune-cle-acces", "Aucune clé d'accès pour ce signataire : utiliser un code de vérification.");
    }
}
