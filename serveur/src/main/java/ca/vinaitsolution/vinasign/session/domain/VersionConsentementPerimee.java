package ca.vinaitsolution.vinasign.session.domain;

import ca.vinaitsolution.vinasign.commun.domain.ErreurMetier;

/** Le texte accepté n'est pas la version en vigueur : le signataire doit relire le texte affiché. */
public class VersionConsentementPerimee extends ErreurMetier {

    public VersionConsentementPerimee() {
        super(Nature.CONFLIT, "consentement-version-perimee", "Le texte du consentement a changé : relire et accepter la version en vigueur.");
    }
}
