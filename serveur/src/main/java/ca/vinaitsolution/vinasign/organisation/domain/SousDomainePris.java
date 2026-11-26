package ca.vinaitsolution.vinasign.organisation.domain;

import ca.vinaitsolution.vinasign.commun.domain.ErreurMetier;

/** Le sous-domaine demandé est déjà celui d'une autre organisation. */
public class SousDomainePris extends ErreurMetier {

    public SousDomainePris() {
        super(Nature.CONFLIT, "sous-domaine-pris", "Ce sous-domaine est déjà attribué : en choisir un autre.");
    }
}
